package com.shopflow.product;

import com.shopflow.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A product in the catalog.
 *
 * Design notes:
 * - sellerId is a plain id, not a @ManyToOne User. Product logic never needs the full
 *   User object, and in Phase 2 products and users live in different services/databases.
 * - Stock changes go through decreaseStock/increaseStock, which enforce the invariant
 *   "stock is never negative" in one place (encapsulation, rich domain model).
 * - @Version enables optimistic locking: concurrent edits are detected, not silently lost.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(Long sellerId, String name, String description, String category,
                   BigDecimal price, int stockQuantity) {
        this.sellerId = sellerId;
        updateDetails(name, description, category, price, stockQuantity);
    }

    public void updateDetails(String name, String description, String category,
                              BigDecimal price, int stockQuantity) {
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("stockQuantity must not be negative");
        }
        this.name = name.trim();
        this.description = description == null ? null : description.trim();
        this.category = category.trim();
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (stockQuantity < quantity) {
            throw new ConflictException("Insufficient stock for '" + name + "': requested "
                    + quantity + ", available " + stockQuantity);
        }
        stockQuantity -= quantity;
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        stockQuantity += quantity;
    }

    /** Soft delete: past orders and carts still reference this product. */
    public void deactivate() {
        this.active = false;
    }

    public boolean isOwnedBy(Long userId) {
        return sellerId.equals(userId);
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public int getStockQuantity() { return stockQuantity; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
