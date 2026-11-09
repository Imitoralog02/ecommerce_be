package com.neora.order.controller;

import com.neora.order.dto.OrderResponse;
import com.neora.order.model.Order;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import com.neora.shared.security.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final OrderRepository orders;
    private final MongoTemplate mongo;

    public AdminOrderController(OrderRepository orders, MongoTemplate mongo) {
        this.orders = orders;
        this.mongo = mongo;
    }

    @GetMapping
    public PageResponse list(@AuthenticationPrincipal AuthenticatedUser user,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        requireAdmin(user);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Query query = Query.query(Criteria.where("status").ne(OrderStatus.ABANDONED));
        long total = mongo.count(query, Order.class);
        query.with(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AdminOrder> content = mongo.find(query, Order.class).stream().map(AdminOrder::from).toList();
        return new PageResponse(content, safePage, safeSize, total,
                total == 0 ? 0 : (int) Math.ceil((double) total / safeSize));
    }

    @GetMapping("/{id}")
    public AdminOrder get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        requireAdmin(user);
        Order order = orders.findById(id).filter(value -> value.status() != OrderStatus.ABANDONED)
                .orElseThrow(() -> new com.neora.shared.error.NotFoundException("Không tìm thấy đơn hàng"));
        return AdminOrder.from(order);
    }

    private static void requireAdmin(AuthenticatedUser user) {
        if (user == null || !"ADMIN".equals(user.role())) {
            throw new AccessDeniedException("Chỉ quản trị viên được xem đơn hàng");
        }
    }

    public record AdminOrder(String userId, OrderResponse order) {
        static AdminOrder from(Order order) { return new AdminOrder(order.userId(), OrderResponse.from(order)); }
    }

    public record PageResponse(List<AdminOrder> content, int page, int size, long totalElements, int totalPages) {}
}
