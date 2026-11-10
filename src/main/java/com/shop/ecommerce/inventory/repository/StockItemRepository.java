package com.shop.ecommerce.inventory.repository;

import com.shop.ecommerce.inventory.entity.StockItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findByProductId(Long productId);
}
