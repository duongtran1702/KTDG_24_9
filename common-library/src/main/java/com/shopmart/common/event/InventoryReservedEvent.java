package com.shopmart.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent implements Serializable {
    private Long orderId;
    private String orderCode;
    private String customerId;
    private String productCode;
    private Integer quantity;
    private BigDecimal totalAmount;
    private Boolean forcePaymentFailure;
}
