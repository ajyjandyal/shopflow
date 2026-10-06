package com.shopflow.order;

import com.shopflow.common.event.StockReservationFailedEvent;
import com.shopflow.common.event.StockReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderEventProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(OrderEventProcessor.class);

    private final OrderRepository orderRepository;

    public OrderEventProcessor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void handleReserved(StockReservedEvent event) {
        Order order = orderRepository.findWithItemsById(event.orderId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Order not found: " + event.orderId()
                        ));

        validateReservation(order, event.reservationId());

        if (order.getStatus() == OrderStatus.PENDING) {
            order.changeStatus(OrderStatus.CONFIRMED);

            log.info(
                    "Order id={} confirmed after stock reservation {}",
                    order.getId(),
                    event.reservationId()
            );
        } else {
            log.info(
                    "Ignoring duplicate stock-reserved event for order id={} status={}",
                    order.getId(),
                    order.getStatus()
            );
        }
    }

    @Transactional
    public void handleReservationFailed(
            StockReservationFailedEvent event
    ) {
        Order order = orderRepository.findWithItemsById(event.orderId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Order not found: " + event.orderId()
                        ));

        validateReservation(order, event.reservationId());

        if (order.getStatus() == OrderStatus.PENDING) {
            order.changeStatus(OrderStatus.CANCELLED);

            log.warn(
                    "Order id={} cancelled because stock reservation failed: {}",
                    order.getId(),
                    event.reason()
            );
        } else {
            log.info(
                    "Ignoring duplicate reservation-failed event for order id={} status={}",
                    order.getId(),
                    order.getStatus()
            );
        }
    }

    private void validateReservation(
            Order order,
            java.util.UUID reservationId
    ) {
        if (!order.getReservationId().equals(reservationId)) {
            throw new IllegalStateException(
                    "Reservation mismatch for order " + order.getId()
            );
        }
    }
}
