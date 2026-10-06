package com.shopflow.cart;

import com.shopflow.cart.dto.AddCartItemRequest;
import com.shopflow.cart.dto.CartItemResponse;
import com.shopflow.cart.dto.CartResponse;
import com.shopflow.cart.dto.UpdateCartItemRequest;
import com.shopflow.catalog.ProductClient;
import com.shopflow.catalog.ProductSnapshot;
import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rule followed throughout: NEVER keep a database transaction open while waiting on a
 * network call. A transaction holds a pooled DB connection; if product-service is slow,
 * all connections would be stuck waiting and the whole service would freeze.
 * So: remote calls happen outside, short TransactionTemplate blocks do the DB work.
 */
@Service
public class CartService {

    static final int MAX_QUANTITY_PER_ITEM = 99;

    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;

    public CartService(CartRepository cartRepository, ProductClient productClient,
                       TransactionTemplate transactionTemplate) {
        this.cartRepository = cartRepository;
        this.productClient = productClient;
        this.transactionTemplate = transactionTemplate;
    }

    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(CartResponse::empty);
    }

    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductSnapshot product = loadAvailableProduct(request.productId()); // remote, no transaction

        Cart cart = transactionTemplate.execute(status -> {
            Cart c = cartRepository.findByUserId(userId)
                    .orElseGet(() -> cartRepository.save(new Cart(userId)));
            int current = c.findItem(product.id()).map(CartItem::getQuantity).orElse(0);
            int newQuantity = current + request.quantity();
            validateQuantity(product, newQuantity);
            c.setItemQuantity(product.id(), newQuantity);
            return c;
        });
        return toResponse(cart);
    }

    public CartResponse updateItem(Long userId, Long productId, UpdateCartItemRequest request) {
        requireInCart(userId, productId);
        ProductSnapshot product = loadAvailableProduct(productId);
        validateQuantity(product, request.quantity());

        Cart cart = transactionTemplate.execute(status -> {
            Cart c = loadCartContaining(userId, productId);
            c.setItemQuantity(productId, request.quantity());
            return c;
        });
        return toResponse(cart);
    }

    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = transactionTemplate.execute(status -> {
            Cart c = loadCartContaining(userId, productId);
            c.removeItem(productId);
            return c;
        });
        return toResponse(cart);
    }

    public void clear(Long userId) {
        transactionTemplate.executeWithoutResult(status ->
                cartRepository.findByUserId(userId).ifPresent(Cart::clear));
    }

    private void requireInCart(Long userId, Long productId) {
        loadCartContaining(userId, productId);
    }

    private Cart loadCartContaining(Long userId, Long productId) {
        return cartRepository.findByUserId(userId)
                .filter(cart -> cart.findItem(productId).isPresent())
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in your cart"));
    }

    private ProductSnapshot loadAvailableProduct(Long productId) {
        return productClient.getSnapshots(List.of(productId)).stream()
                .filter(ProductSnapshot::active)
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));
    }

    /** A friendly early check. The authoritative check happens in product-service at checkout. */
    private void validateQuantity(ProductSnapshot product, int quantity) {
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new BadRequestException("Maximum " + MAX_QUANTITY_PER_ITEM + " units per product");
        }
        if (quantity > product.stockQuantity()) {
            throw new ConflictException("Only " + product.stockQuantity() + " units of '"
                    + product.name() + "' are available");
        }
    }

    /** One batched HTTP call for all products in the cart (the network version of avoiding N+1). */
    private CartResponse toResponse(Cart cart) {
        Set<Long> productIds = cart.getItems().stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Map<Long, ProductSnapshot> productsById = productClient.getSnapshots(productIds).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));

        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> CartItemResponse.of(item, productsById.get(item.getProductId())))
                .toList();
        return CartResponse.of(items);
    }
}
