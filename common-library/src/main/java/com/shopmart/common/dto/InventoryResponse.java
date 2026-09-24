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
public class InventoryResponse implements Serializable {
    private boolean success;
    private String productCode;
    private Integer remainingStock;
    private String message;
    private String handledByInstance;
}
