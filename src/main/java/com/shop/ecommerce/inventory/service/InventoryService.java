package com.shop.ecommerce.inventory.service;

import com.shop.ecommerce.common.exception.NotFoundException;
import com.shop.ecommerce.inventory.dto.ReservationLine;
import com.shop.ecommerce.inventory.dto.StockResponse;
import com.shop.ecommerce.inventory.entity.ReservationStatus;
import com.shop.ecommerce.inventory.entity.StockItem;
import com.shop.ecommerce.inventory.entity.StockReservation;
import com.shop.ecommerce.inventory.exception.InsufficientStockException;
import com.shop.ecommerce.inventory.repository.StockItemRepository;
import com.shop.ecommerce.inventory.repository.StockReservationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public StockResponse getStock(Long productId) {
        return stockItemRepository.findByProductId(productId)
                .map(StockResponse::from)
                .orElseGet(() -> StockResponse.empty(productId));
    }

    @Transactional
    public StockResponse setStock(Long productId, int quantityAvailable) {
        StockItem stock = stockItemRepository.findByProductId(productId).orElseGet(() -> {
            StockItem created = new StockItem();
            created.setProductId(productId);
            return created;
        });
        stock.setQuantityAvailable(quantityAvailable);
        return StockResponse.from(stockItemRepository.save(stock));
    }

    @Transactional
    public void reserve(Long orderId, List<ReservationLine> lines) {
        if (!reservationRepository.findByOrderId(orderId).isEmpty()) {
            return;
        }
        for (ReservationLine line : lines) {
            StockItem stock = stockItemRepository.findByProductId(line.productId())
                    .orElseThrow(() -> new InsufficientStockException(line.productId()));

            if (stock.getQuantityAvailable() < line.quantity()) {
                throw new InsufficientStockException(line.productId());
            }

            stock.setQuantityAvailable(stock.getQuantityAvailable() - line.quantity());
            stock.setQuantityReserved(stock.getQuantityReserved() + line.quantity());

            StockReservation reservation = new StockReservation();
            reservation.setOrderId(orderId);
            reservation.setProductId(line.productId());
            reservation.setQuantity(line.quantity());
            reservation.setStatus(ReservationStatus.RESERVED);

            reservationRepository.save(reservation);
        }
    }

    @Transactional
    public void release(Long orderId) {
        for (StockReservation reservation
                : reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED)) {
            StockItem stock = findStock(reservation.getProductId());
            stock.setQuantityAvailable(stock.getQuantityAvailable() + reservation.getQuantity());
            stock.setQuantityReserved(stock.getQuantityReserved() - reservation.getQuantity());
            reservation.setStatus(ReservationStatus.RELEASED);
        }
    }

    @Transactional
    public void commit(Long orderId) {
        for (StockReservation reservation
                : reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED)) {
            StockItem stock = findStock(reservation.getProductId());
            stock.setQuantityReserved(stock.getQuantityReserved() - reservation.getQuantity());
            reservation.setStatus(ReservationStatus.COMMITTED);
        }
    }

    private StockItem findStock(Long productId) {
        return stockItemRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Stock not found for product " + productId));
    }
}
