package com.shopflow.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** List queries return summaries without items: paginating a JOIN FETCH of a collection is unsafe/slow. */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /** Single-order view loads the items in the same query. */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);
}
