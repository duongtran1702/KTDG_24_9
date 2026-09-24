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
public class ProductDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String productCode;
    private String name;
    private BigDecimal price;
    private Integer stockQuantity;
    private String description;
    private String sourceInstance; // For demonstrating load balancing across instances
}
