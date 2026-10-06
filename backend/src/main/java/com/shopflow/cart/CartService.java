package com.shopflow.cart;

import com.shopflow.cart.dto.AddCartItemRequest;
import com.shopflow.cart.dto.CartItemResponse;
import com.shopflow.cart.dto.CartResponse;
import com.shopflow.cart.dto.UpdateCartItemRequest;
import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.product.Product;
import com.shopflow.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartService {

    static final int MAX_QUANTITY_PER_ITEM = 99;

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    /** Read-only: a user without a cart simply gets an empty one (no row is created). */
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(CartResponse::empty);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        Product product = loadActiveProduct(request.productId());
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(new Cart(userId)));

        int currentQuantity = cart.findItem(product.getId()).map(CartItem::getQuantity).orElse(0);
        int newQuantity = currentQuantity + request.quantity();
        validateQuantity(product, newQuantity);

        cart.setItemQuantity(product.getId(), newQuantity);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long productId, UpdateCartItemRequest request) {
        Cart cart = loadCartContaining(userId, productId);
        Product product = loadActiveProduct(productId);
        validateQuantity(product, request.quantity());
        cart.setItemQuantity(productId, request.quantity());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = loadCartContaining(userId, productId);
        cart.removeItem(productId);
        return toResponse(cart);
    }

    @Transactional
    public void clear(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(Cart::clear);
    }

    private Cart loadCartContaining(Long userId, Long productId) {
        return cartRepository.findByUserId(userId)
                .filter(cart -> cart.findItem(productId).isPresent())
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in your cart"));
    }

    private Product loadActiveProduct(Long productId) {
        return productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));
    }

    /**
     * This is a friendly early check only. Stock is NOT reserved here; the authoritative
     * check happens under a row lock at checkout, because stock can change at any moment.
     */
    private void validateQuantity(Product product, int quantity) {
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new BadRequestException("Maximum " + MAX_QUANTITY_PER_ITEM + " units per product");
        }
        if (quantity > product.getStockQuantity()) {
            throw new ConflictException("Only " + product.getStockQuantity() + " units of '"
                    + product.getName() + "' are available");
        }
    }

    /**
     * Builds the response with ONE query for all products (WHERE id IN (...)), then
     * matches them via a HashMap in O(1) each. Looping and calling findById per item
     * would be the classic N+1 query problem.
     */
    private CartResponse toResponse(Cart cart) {
        Set<Long> productIds = cart.getItems().stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());
        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> CartItemResponse.of(item, productsById.get(item.getProductId())))
                .toList();
        return CartResponse.of(items);
    }
}
