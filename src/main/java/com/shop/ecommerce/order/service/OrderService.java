package com.shop.ecommerce.order.service;

import com.shop.ecommerce.cart.dto.CartItemResponse;
import com.shop.ecommerce.cart.dto.CartResponse;
import com.shop.ecommerce.cart.service.CartService;
import com.shop.ecommerce.catalog.dto.ProductResponse;
import com.shop.ecommerce.catalog.service.ProductService;
import com.shop.ecommerce.common.dto.PageResponse;
import com.shop.ecommerce.common.exception.BusinessRuleException;
import com.shop.ecommerce.common.exception.NotFoundException;
import com.shop.ecommerce.inventory.dto.ReservationLine;
import com.shop.ecommerce.inventory.service.InventoryService;
import com.shop.ecommerce.order.dto.OrderResponse;
import com.shop.ecommerce.order.entity.Order;
import com.shop.ecommerce.order.entity.OrderItem;
import com.shop.ecommerce.order.entity.OrderStatus;
import com.shop.ecommerce.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final InventoryService inventoryService;

    @Transactional
    public OrderResponse createOrder(Long userId, String idempotencyKey) {
        Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }

        CartResponse cart = cartService.getCart(userId);
        if (cart.items().isEmpty()) {
            throw new BusinessRuleException("Cart is empty");
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        String currency = null;

        for (CartItemResponse cartItem : cart.items()) {
            ProductResponse product = loadProduct(cartItem.productId());

            if (currency == null) {
                currency = product.currency();
            } else if (!currency.equals(product.currency())) {
                throw new BusinessRuleException("All items in an order must use the same currency");
            }

            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setSku(product.sku());
            item.setProductName(product.name());
            item.setUnitPrice(product.price());
            item.setQuantity(cartItem.quantity());
            order.addItem(item);

            total = total.add(product.price().multiply(BigDecimal.valueOf(cartItem.quantity())));
        }

        order.setTotalAmount(total);
        order.setCurrency(currency);
        Order saved = orderRepository.save(order);

        List<ReservationLine> lines = saved.getItems().stream()
                .map(item -> new ReservationLine(item.getProductId(), item.getQuantity()))
                .toList();
        inventoryService.reserve(saved.getId(), lines);
        transition(saved, OrderStatus.STOCK_RESERVED);

        cartService.clear(userId);

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(Long userId, Pageable pageable) {
        return PageResponse.from(orderRepository.findByUserId(userId, pageable), OrderResponse::from);
    }

    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new BusinessRuleException("An order in status " + order.getStatus() + " cannot be cancelled");
        }

        if (order.getStatus() == OrderStatus.STOCK_RESERVED) {
            inventoryService.release(orderId);
        }
        transition(order, OrderStatus.CANCELLED);

        return OrderResponse.from(order);
    }

    private ProductResponse loadProduct(Long productId) {
        try {
            return productService.getById(productId);
        } catch (NotFoundException ex) {
            throw new BusinessRuleException("Product is no longer available: " + productId);
        }
    }

    private void transition(Order order, OrderStatus next) {
        if (!order.getStatus().canTransitionTo(next)) {
            throw new BusinessRuleException("Invalid order transition " + order.getStatus() + " -> " + next);
        }
        order.setStatus(next);
    }
}
