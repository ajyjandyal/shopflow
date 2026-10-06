package com.shopflow.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void cancellationIsOnlyAllowedBeforeShipping() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void terminalStatesAllowNoTransitions() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target)).isFalse();
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
        assertThat(OrderStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void cannotSkipSteps() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }
}
