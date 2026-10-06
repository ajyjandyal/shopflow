package com.shopflow.product;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Composable, type-safe query filters. Each method builds one WHERE condition; the
 * service combines only the ones the client actually asked for. Values are always
 * bound as parameters, never concatenated into SQL, which prevents SQL injection.
 */
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> nameContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    public static Specification<Product> categoryEquals(String category) {
        String normalized = category.toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), normalized);
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    /** Stops users from injecting LIKE wildcards: searching "50%" should match the text "50%". */
    static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
