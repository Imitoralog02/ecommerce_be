package com.neora.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class AuthHeaderFilter extends OncePerRequestFilter {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String auth;
    private final String catalog;
    private final String cart;
    private final String order;
    private final String notification;
    private final String customer;

    public AuthHeaderFilter(@Value("${services.auth}") String auth,
                            @Value("${services.catalog}") String catalog,
                            @Value("${services.cart}") String cart,
                            @Value("${services.order}") String order,
                            @Value("${services.notification}") String notification,
                            @Value("${services.customer}") String customer) {
        this.auth = auth; this.catalog = catalog; this.cart = cart; this.order = order;
        this.notification = notification; this.customer = customer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String query = request.getQueryString();
            URI uri = URI.create(route(request.getRequestURI()) + request.getRequestURI()
                    + (query == null ? "" : "?" + query));
            byte[] body = request.getInputStream().readAllBytes();
            HttpRequest.Builder outbound = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .method(request.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(body));
            copy(request, outbound, HttpHeaders.AUTHORIZATION);
            copy(request, outbound, HttpHeaders.CONTENT_TYPE);
            copy(request, outbound, "Idempotency-Key");
            HttpResponse<byte[]> upstream = client.send(outbound.build(), HttpResponse.BodyHandlers.ofByteArray());
            response.setStatus(upstream.statusCode());
            upstream.headers().firstValue("content-type").ifPresent(response::setContentType);
            upstream.headers().firstValue("cache-control").ifPresent(value -> response.setHeader(HttpHeaders.CACHE_CONTROL, value));
            response.getOutputStream().write(upstream.body());
        } catch (IOException ex) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Upstream service unavailable");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Upstream request interrupted");
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpStatus.NOT_FOUND.value());
        }
    }

    private String route(String path) {
        if (matches(path, "/api/v1/auth") || path.equals("/api/v1/me")) return auth;
        if (matches(path, "/api/v1/products") || matches(path, "/api/v1/categories")) return catalog;
        if (matches(path, "/api/v1/cart")) return cart;
        if (matches(path, "/api/v1/orders")) return order;
        if (matches(path, "/api/v1/notifications")) return notification;
        if (matches(path, "/api/v1/addresses") || matches(path, "/api/v1/wishlist")) return customer;
        throw new IllegalArgumentException("Unknown route");
    }

    private boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private void copy(HttpServletRequest source, HttpRequest.Builder target, String name) {
        String value = source.getHeader(name);
        if (value != null) target.header(name, value);
    }
}
