package com.shopmart.inventory.controller;

import com.shopmart.common.dto.InventoryDeductRequest;
import com.shopmart.common.dto.InventoryResponse;
import com.shopmart.common.dto.ProductDto;
import com.shopmart.common.event.InventoryCompensateEvent;
import com.shopmart.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Value("${server.port:8082}")
    private String serverPort;

    @GetMapping("/{productCode}")
    public ResponseEntity<ProductDto> getProductByCode(@PathVariable String productCode) {
        log.info("[HTTP GET /api/inventory/{}] Request received on instance port: {}", productCode, serverPort);
        ProductDto product = inventoryService.getProductByCode(productCode);
        return ResponseEntity.ok(product);
    }

    @PostMapping("/deduct")
    public ResponseEntity<InventoryResponse> deductStock(@RequestBody InventoryDeductRequest request) {
        log.info("[HTTP POST /api/inventory/deduct] Order: {}, Product: {}, Quantity: {} on port: {}",
                request.getOrderId(), request.getProductCode(), request.getQuantity(), serverPort);
        InventoryResponse response = inventoryService.deductStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/compensate")
    public ResponseEntity<InventoryResponse> compensateStock(@RequestBody InventoryCompensateEvent event) {
        log.info("[HTTP POST /api/inventory/compensate] Order: {}, Product: {}, Quantity: {} on port: {}",
                event.getOrderId(), event.getProductCode(), event.getQuantity(), serverPort);
        InventoryResponse response = inventoryService.compensateStock(event);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ProductDto> createOrUpdateProduct(@RequestBody ProductDto productDto) {
        log.info("[HTTP POST /api/inventory] Saving product: {}", productDto.getProductCode());
        ProductDto saved = inventoryService.updateProduct(productDto);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{productCode}")
    public ResponseEntity<String> deleteProduct(@PathVariable String productCode) {
        log.info("[HTTP DELETE /api/inventory/{}] Deleting product", productCode);
        inventoryService.deleteProduct(productCode);
        return ResponseEntity.ok("Product deleted and evicted from cache: " + productCode);
    }

    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearCache() {
        log.info("[HTTP POST /api/inventory/cache/clear] Clearing Redis cache");
        inventoryService.evictAllCache();
        return ResponseEntity.ok("Redis cache for products cleared successfully");
    }

    @GetMapping("/all")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        log.info("[HTTP GET /api/inventory/all] Request on port: {}", serverPort);
        return ResponseEntity.ok(inventoryService.getAllProducts());
    }

    @GetMapping("/instance-info")
    public ResponseEntity<String> getInstanceInfo() {
        return ResponseEntity.ok("Inventory Service running on port: " + serverPort);
    }
}
