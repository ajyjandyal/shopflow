package com.shopflow.order;

import com.shopflow.common.web.PageResponse;
import com.shopflow.common.web.SortValidator;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
import com.shopflow.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalAmount", "status");

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place an order from my current cart")
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal AuthenticatedUser user) {
        OrderResponse order = orderService.placeOrder(user.id());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(order.id()).toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping
    @Operation(summary = "List my orders (newest first by default)")
    public PageResponse<OrderSummaryResponse> listMyOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable, SORTABLE_FIELDS);
        return orderService.listMyOrders(user.id(), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my orders with its items (ADMIN can view any order)")
    public OrderResponse getOrder(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.getOrder(id, user);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel my order (only before it ships); restores stock")
    public OrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.cancel(id, user);
    }
}
