package com.neora.order.controller;

import com.neora.order.model.OrderStatus;
import com.neora.order.service.OrderCancellationService;
import com.neora.order.service.OrderFulfillmentService;
import com.neora.order.service.OrderService;
import com.neora.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderControllerTest {
    @Test
    void customerCannotAdvanceFulfillment() {
        OrderFulfillmentService fulfillment = mock(OrderFulfillmentService.class);
        OrderController controller = new OrderController(mock(OrderService.class),
                mock(OrderCancellationService.class), fulfillment);

        assertThatThrownBy(() -> controller.advance(
                new AuthenticatedUser("user-1", "customer@example.com", "CUSTOMER"),
                "order-1", new OrderController.FulfillmentRequest(OrderStatus.SHIPPED, "TRACK-1", null)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(fulfillment);
    }
}
