package com.shopflow.cart;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One cart per user (enforced by a UNIQUE constraint on user_id).
 * Cart is the "aggregate root": items are only changed through Cart's methods, and
 * cascade + orphanRemoval make saving/removing items automatic.
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CartItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {
    }

    public Cart(Long userId) {
        this.userId = userId;
    }

    public Optional<CartItem> findItem(Long productId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    /** Adds the product or overwrites its quantity if it is already in the cart. */
    public void setItemQuantity(Long productId, int quantity) {
        findItem(productId).ifPresentOrElse(
                item -> item.setQuantity(quantity),
                () -> items.add(new CartItem(this, productId, quantity)));
    }

    public boolean removeItem(Long productId) {
        return items.removeIf(item -> item.getProductId().equals(productId));
    }

    public void clear() {
        items.clear();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    /** Read-only view: callers cannot bypass the methods above. */
    public List<CartItem> getItems() { return Collections.unmodifiableList(items); }
}
