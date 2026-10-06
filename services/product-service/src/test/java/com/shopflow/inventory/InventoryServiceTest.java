package com.shopflow.inventory;

import com.shopflow.common.exception.ConflictException;
import com.shopflow.inventory.dto.ReservationResponse;
import com.shopflow.inventory.dto.ReserveStockRequest;
import com.shopflow.product.Product;
import com.shopflow.product.ProductRepository;
import com.shopflow.product.ProductsChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private final UUID reservationId = UUID.randomUUID();

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void reserveTakesStockAndRecordsPriceSnapshot() {
        StockReservation pending = StockReservation.pending(reservationId);
        Product keyboard = product(1L, 5);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(pending));
        when(productRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(keyboard));

        ReservationResponse response = inventoryService.reserve(request(1L, 2));

        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(keyboard.getStockQuantity()).isEqualTo(3);
        assertThat(response.lines()).singleElement().satisfies(line -> {
            assertThat(line.productName()).isEqualTo("Keyboard");
            assertThat(line.unitPrice()).isEqualByComparingTo("49.99");
            assertThat(line.quantity()).isEqualTo(2);
        });
        verify(reservationRepository).insertPendingIfAbsent(reservationId);
        // Stock changed, so the cached product view must be evicted (after commit).
        verify(eventPublisher).publishEvent(new ProductsChangedEvent(Set.of(1L)));
    }

    @Test
    void repeatedReserveReturnsSameResultWithoutTakingStockAgain() {
        StockReservation existing = StockReservation.pending(reservationId);
        existing.markReserved(List.of(new ReservationLine(1L, "Keyboard", new BigDecimal("49.99"), 2)));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(existing));

        ReservationResponse response = inventoryService.reserve(request(1L, 2));

        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        verifyNoInteractions(productRepository);
        verifyNoInteractions(eventPublisher); // replay: stock unchanged, nothing to evict
    }

    @Test
    void reserveAfterReleaseIsRejected() {
        StockReservation tombstone = StockReservation.pending(reservationId);
        tombstone.markReleased();
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(tombstone));

        assertThatThrownBy(() -> inventoryService.reserve(request(1L, 2))).isInstanceOf(ConflictException.class);
        verifyNoInteractions(productRepository);
    }

    @Test
    void insufficientStockIsRejected() {
        StockReservation pending = StockReservation.pending(reservationId);
        Product keyboard = product(1L, 1);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(pending));
        when(productRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(keyboard));

        assertThatThrownBy(() -> inventoryService.reserve(request(1L, 2))).isInstanceOf(ConflictException.class);
        assertThat(keyboard.getStockQuantity()).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseReturnsStockExactlyOnce() {
        StockReservation reserved = StockReservation.pending(reservationId);
        reserved.markReserved(List.of(new ReservationLine(1L, "Keyboard", new BigDecimal("49.99"), 2)));
        Product keyboard = product(1L, 3);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reserved));
        when(productRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(keyboard));

        inventoryService.release(reservationId);
        ReservationResponse second = inventoryService.release(reservationId);

        assertThat(keyboard.getStockQuantity()).isEqualTo(5);
        assertThat(second.status()).isEqualTo(ReservationStatus.RELEASED);
        verify(productRepository, times(1)).findAllByIdForUpdate(anyCollection());
        // Only the first release changed stock, so exactly one eviction event.
        verify(eventPublisher, times(1)).publishEvent(new ProductsChangedEvent(Set.of(1L)));
    }

    @Test
    void releaseOfUnknownReservationStoresTombstone() {
        StockReservation pending = StockReservation.pending(reservationId);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(pending));

        ReservationResponse response = inventoryService.release(reservationId);

        assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
        verifyNoInteractions(productRepository);
        verifyNoInteractions(eventPublisher);
    }

    private ReserveStockRequest request(Long productId, int quantity) {
        return new ReserveStockRequest(reservationId, List.of(new ReserveStockRequest.Item(productId, quantity)));
    }

    private static Product product(Long id, int stock) {
        Product product = new Product(50L, "Keyboard", null, "electronics", new BigDecimal("49.99"), stock);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
