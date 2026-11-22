package com.shop.ecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.ecommerce.inventory.dto.ReservationLine;
import com.shop.ecommerce.inventory.entity.ReservationStatus;
import com.shop.ecommerce.inventory.entity.StockItem;
import com.shop.ecommerce.inventory.entity.StockReservation;
import com.shop.ecommerce.inventory.exception.InsufficientStockException;
import com.shop.ecommerce.inventory.repository.StockItemRepository;
import com.shop.ecommerce.inventory.repository.StockReservationRepository;
import com.shop.ecommerce.inventory.service.InventoryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    StockItemRepository stockItemRepository;

    @Mock
    StockReservationRepository reservationRepository;

    @InjectMocks
    InventoryService inventoryService;

    private StockItem stock(Long productId, int available, int reserved) {
        StockItem stock = new StockItem();
        stock.setProductId(productId);
        stock.setQuantityAvailable(available);
        stock.setQuantityReserved(reserved);
        return stock;
    }

    private StockReservation reservation(Long orderId, Long productId, int quantity) {
        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);
        reservation.setProductId(productId);
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.RESERVED);
        return reservation;
    }

    @Test
    void reserve_movesQuantityFromAvailableToReserved() {
        StockItem stock = stock(1L, 10, 0);
        when(reservationRepository.findByOrderId(100L)).thenReturn(List.of());
        when(stockItemRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        inventoryService.reserve(100L, List.of(new ReservationLine(1L, 3)));

        assertThat(stock.getQuantityAvailable()).isEqualTo(7);
        assertThat(stock.getQuantityReserved()).isEqualTo(3);
        verify(reservationRepository).save(any(StockReservation.class));
    }

    @Test
    void reserve_notEnoughStock_throwsAndSavesNothing() {
        when(reservationRepository.findByOrderId(100L)).thenReturn(List.of());
        when(stockItemRepository.findByProductId(1L)).thenReturn(Optional.of(stock(1L, 2, 0)));

        assertThatThrownBy(() -> inventoryService.reserve(100L, List.of(new ReservationLine(1L, 3))))
                .isInstanceOf(InsufficientStockException.class);
        verify(reservationRepository, never()).save(any(StockReservation.class));
    }

    @Test
    void reserve_productWithoutStockRow_isTreatedAsOutOfStock() {
        when(reservationRepository.findByOrderId(100L)).thenReturn(List.of());
        when(stockItemRepository.findByProductId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserve(100L, List.of(new ReservationLine(1L, 1))))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void reserve_calledTwiceForSameOrder_isNoOp() {
        when(reservationRepository.findByOrderId(100L)).thenReturn(List.of(reservation(100L, 1L, 3)));

        inventoryService.reserve(100L, List.of(new ReservationLine(1L, 3)));

        verify(stockItemRepository, never()).findByProductId(any());
    }

    @Test
    void release_returnsReservedQuantityToAvailable() {
        StockItem stock = stock(1L, 7, 3);
        StockReservation reservation = reservation(100L, 1L, 3);
        when(reservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockItemRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        inventoryService.release(100L);

        assertThat(stock.getQuantityAvailable()).isEqualTo(10);
        assertThat(stock.getQuantityReserved()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void commit_removesReservedQuantityForGood() {
        StockItem stock = stock(1L, 7, 3);
        StockReservation reservation = reservation(100L, 1L, 3);
        when(reservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockItemRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        inventoryService.commit(100L);

        assertThat(stock.getQuantityAvailable()).isEqualTo(7);
        assertThat(stock.getQuantityReserved()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void releaseAfterNothingReserved_doesNothing() {
        when(reservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.RESERVED)).thenReturn(List.of());

        inventoryService.release(100L);

        verify(stockItemRepository, never()).findByProductId(any());
    }
}
