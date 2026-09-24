package com.shopmart.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductRequest implements Serializable {
    private String productCode;
    private Integer quantity;
    private Long orderId;
}
