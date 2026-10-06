package com.shopflow.cart;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /** Loads the cart AND its items in a single query (JOIN) instead of 1 + 1 queries. */
    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByUserId(Long userId);
}
