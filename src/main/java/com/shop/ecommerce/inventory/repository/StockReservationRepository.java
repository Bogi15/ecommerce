package com.shop.ecommerce.inventory.repository;

import com.shop.ecommerce.inventory.entity.ReservationStatus;
import com.shop.ecommerce.inventory.entity.StockReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderId(Long orderId);

    List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);
}
