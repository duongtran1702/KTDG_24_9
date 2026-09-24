package com.shopmart.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCompensateEvent implements Serializable {
    private Long orderId;
    private String orderCode;
    private String productCode;
    private Integer quantity;
    private String reason;
}
