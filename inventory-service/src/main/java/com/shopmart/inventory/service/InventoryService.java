package com.shopmart.inventory.service;

import com.shopmart.common.dto.InventoryDeductRequest;
import com.shopmart.common.dto.InventoryResponse;
import com.shopmart.common.dto.ProductDto;
import com.shopmart.common.event.InventoryCompensateEvent;

import java.util.List;

public interface InventoryService {
    ProductDto getProductByCode(String productCode);
    ProductDto updateProduct(ProductDto productDto);
    void deleteProduct(String productCode);
    InventoryResponse deductStock(InventoryDeductRequest request);
    InventoryResponse compensateStock(InventoryCompensateEvent event);
    List<ProductDto> getAllProducts();
    void evictAllCache();
}
