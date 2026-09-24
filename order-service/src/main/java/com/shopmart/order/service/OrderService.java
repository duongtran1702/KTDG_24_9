package com.shopmart.order.service;

import com.shopmart.common.dto.OrderRequest;
import com.shopmart.common.dto.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrderSync(OrderRequest request);
    OrderResponse createOrderSaga(OrderRequest request);
    void confirmOrder(Long orderId);
    void cancelOrder(Long orderId, String reason);
    OrderResponse getOrderById(Long orderId);
    List<OrderResponse> getAllOrders();
}
