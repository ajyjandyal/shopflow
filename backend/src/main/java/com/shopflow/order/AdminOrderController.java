package com.shopflow.order;

import com.shopflow.common.web.PageResponse;
import com.shopflow.common.web.SortValidator;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
import com.shopflow.order.dto.UpdateOrderStatusRequest;
import com.shopflow.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints. Protected twice ("defence in depth"): the URL rule in SecurityConfig
 * AND @PreAuthorize here, so a future routing mistake cannot expose them.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "List all orders, optionally filtered by status")
    public PageResponse<OrderSummaryResponse> listAll(
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable, OrderController.SORTABLE_FIELDS);
        return orderService.listAll(status, pageable);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an order to a new status (must be a valid transition)")
    public OrderResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser admin) {
        return orderService.updateStatus(id, request.status(), admin.id());
    }
}
