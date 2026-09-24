package com.shopmart.common.dto;

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
public class OrderRequest implements Serializable {
    private String customerId;
    private String productCode;
    private Integer quantity;
    private BigDecimal price;
    private Boolean forcePaymentFailure; // Flag to deliberately test Saga rollback
}
