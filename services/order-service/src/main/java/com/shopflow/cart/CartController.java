package com.shopflow.cart;

import com.shopflow.cart.dto.AddCartItemRequest;
import com.shopflow.cart.dto.CartResponse;
import com.shopflow.cart.dto.UpdateCartItemRequest;
import com.shopflow.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every endpoint operates on the caller's own cart; there is no cartId in any URL. */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get my cart with current prices and availability")
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.getCart(user.id());
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product to my cart (adds to existing quantity)")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.addItem(user.id(), request);
    }

    @PatchMapping("/items/{productId}")
    @Operation(summary = "Set the quantity of a product in my cart")
    public CartResponse updateItem(@PathVariable Long productId,
                                   @Valid @RequestBody UpdateCartItemRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.updateItem(user.id(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a product from my cart")
    public CartResponse removeItem(@PathVariable Long productId,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.removeItem(user.id(), productId);
    }

    @DeleteMapping
    @Operation(summary = "Empty my cart")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal AuthenticatedUser user) {
        cartService.clear(user.id());
        return ResponseEntity.noContent().build();
    }
}
