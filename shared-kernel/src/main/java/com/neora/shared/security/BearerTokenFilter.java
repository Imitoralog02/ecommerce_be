package com.neora.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final JwtProperties properties;
    private final ObjectMapper objectMapper;

    public BearerTokenFilter(JwtProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            JwtClaims claims = verify(token);
            if (claims != null) {
                    var principal = new AuthenticatedUser(claims.sub(), claims.email(), claims.role());
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            principal, token, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))));
            }
        }
        chain.doFilter(request, response);
    }

    private JwtClaims verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(expected, actual)) return null;
            JwtClaims claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), JwtClaims.class);
            return claims.exp() > Instant.now().getEpochSecond() ? claims : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record JwtClaims(String sub, String email, String role, long iat, long exp) {}
}
