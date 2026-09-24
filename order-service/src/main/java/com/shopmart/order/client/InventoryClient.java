package com.shopmart.order.client;

import com.shopmart.common.dto.InventoryDeductRequest;
import com.shopmart.common.dto.InventoryResponse;
import com.shopmart.common.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @GetMapping("/api/inventory/{productCode}")
    ProductDto getProductByCode(@PathVariable("productCode") String productCode);

    @PostMapping("/api/inventory/deduct")
    InventoryResponse deductStock(@RequestBody InventoryDeductRequest request);
}
