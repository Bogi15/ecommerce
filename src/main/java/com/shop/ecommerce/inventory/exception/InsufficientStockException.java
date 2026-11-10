package com.shop.ecommerce.inventory.exception;

import com.shop.ecommerce.common.exception.BusinessRuleException;

public class InsufficientStockException extends BusinessRuleException {

    public InsufficientStockException(Long productId) {
        super("Insufficient stock for product " + productId);
    }
}
