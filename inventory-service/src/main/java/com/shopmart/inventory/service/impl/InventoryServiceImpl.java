package com.shopmart.inventory.service.impl;

import com.shopmart.common.dto.InventoryDeductRequest;
import com.shopmart.common.dto.InventoryResponse;
import com.shopmart.common.dto.ProductDto;
import com.shopmart.common.event.InventoryCompensateEvent;
import com.shopmart.inventory.entity.Product;
import com.shopmart.inventory.repository.ProductRepository;
import com.shopmart.inventory.service.InventoryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;

    @Value("${server.port:8082}")
    private String serverPort;

    @PostConstruct
    public void initProducts() {
        if (productRepository.count() == 0) {
            log.info("[INVENTORY INIT] Seeding default products into Database...");
            productRepository.save(Product.builder()
                    .productCode("PROD-001")
                    .name("iPhone 15 Pro Max")
                    .price(new BigDecimal("30000000"))
                    .stockQuantity(100)
                    .description("Apple flagship smartphone")
                    .build());

            productRepository.save(Product.builder()
                    .productCode("PROD-002")
                    .name("MacBook Pro 16 M3")
                    .price(new BigDecimal("50000000"))
                    .stockQuantity(50)
                    .description("Apple professional laptop")
                    .build());

            productRepository.save(Product.builder()
                    .productCode("PROD-003")
                    .name("Sony WH-1000XM5")
                    .price(new BigDecimal("8000000"))
                    .stockQuantity(200)
                    .description("Premium noise cancelling headphones")
                    .build());

            productRepository.save(Product.builder()
                    .productCode("PROD-OUT-OF-STOCK")
                    .name("Limited Edition Collector Item")
                    .price(new BigDecimal("15000000"))
                    .stockQuantity(0)
                    .description("Sold out item for testing failure path")
                    .build());
            log.info("[INVENTORY INIT] Default products seeded successfully.");
        }
    }

    /**
     * Cache-Aside Pattern:
     * If present in Redis -> returned directly (method body not executed).
     * If not present -> method executes, queries DB, and stores into Redis.
     */
    @Override
    @Cacheable(value = "products", key = "#productCode", unless = "#result == null")
    @Transactional(readOnly = true)
    public ProductDto getProductByCode(String productCode) {
        log.info("[CACHE MISS -> DATABASE QUERY] Querying database for productCode: '{}' on instance port: {}",
                productCode, serverPort);
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productCode));

        return mapToDto(product);
    }

    /**
     * Update product and update the Redis cache entry immediately
     */
    @Override
    @CachePut(value = "products", key = "#productDto.productCode")
    @Transactional
    public ProductDto updateProduct(ProductDto productDto) {
        log.info("[CACHE-PUT -> DB UPDATE & CACHE REFRESH] Updating productCode: '{}'", productDto.getProductCode());
        Product product = productRepository.findByProductCode(productDto.getProductCode())
                .orElse(Product.builder()
                        .productCode(productDto.getProductCode())
                        .build());

        product.setName(productDto.getName());
        product.setPrice(productDto.getPrice());
        product.setStockQuantity(productDto.getStockQuantity());
        product.setDescription(productDto.getDescription());

        Product saved = productRepository.save(product);
        return mapToDto(saved);
    }

    /**
     * Delete product and evict from Redis cache
     */
    @Override
    @CacheEvict(value = "products", key = "#productCode")
    @Transactional
    public void deleteProduct(String productCode) {
        log.info("[CACHE-EVICT] Evicting product from cache and DB: '{}'", productCode);
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productCode));
        productRepository.delete(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public void evictAllCache() {
        log.info("[CACHE-EVICT ALL] Cleared all products from Redis cache.");
    }

    /**
     * Synchronous deduction (called via OpenFeign with Circuit Breaker)
     */
    @Override
    @Transactional
    public synchronized InventoryResponse deductStock(InventoryDeductRequest request) {
        log.info("[INVENTORY DEDUCT] Instance on port {} processing deduction for orderId: {}, product: {}, quantity: {}",
                serverPort, request.getOrderId(), request.getProductCode(), request.getQuantity());

        Product product = productRepository.findByProductCode(request.getProductCode())
                .orElse(null);

        if (product == null) {
            log.error("[INVENTORY DEDUCT FAILED] Product not found: {}", request.getProductCode());
            return InventoryResponse.builder()
                    .success(false)
                    .productCode(request.getProductCode())
                    .remainingStock(0)
                    .message("Product not found: " + request.getProductCode())
                    .handledByInstance(serverPort)
                    .build();
        }

        if (product.getStockQuantity() < request.getQuantity()) {
            log.warn("[INVENTORY DEDUCT FAILED] Insufficient stock. Available: {}, Requested: {}",
                    product.getStockQuantity(), request.getQuantity());
            return InventoryResponse.builder()
                    .success(false)
                    .productCode(request.getProductCode())
                    .remainingStock(product.getStockQuantity())
                    .message("Insufficient stock. Available: " + product.getStockQuantity())
                    .handledByInstance(serverPort)
                    .build();
        }

        int previousStock = product.getStockQuantity();
        product.setStockQuantity(previousStock - request.getQuantity());
        productRepository.save(product);

        log.info("[INVENTORY DEDUCT SUCCESS] Product: {}, Previous Stock: {}, Deducted: {}, Remaining Stock: {} [Instance: {}]",
                product.getProductCode(), previousStock, request.getQuantity(), product.getStockQuantity(), serverPort);

        return InventoryResponse.builder()
                .success(true)
                .productCode(product.getProductCode())
                .remainingStock(product.getStockQuantity())
                .message("Stock deducted successfully")
                .handledByInstance(serverPort)
                .build();
    }

    /**
     * Saga Compensating Transaction: Roll back stock deduction
     */
    @Override
    @Transactional
    public synchronized InventoryResponse compensateStock(InventoryCompensateEvent event) {
        log.info("[SAGA COMPENSATION - INVENTORY ROLLBACK] OrderId: {}, Product: {}, Quantity to restore: {}, Reason: '{}'",
                event.getOrderId(), event.getProductCode(), event.getQuantity(), event.getReason());

        Product product = productRepository.findByProductCode(event.getProductCode()).orElse(null);
        if (product == null) {
            log.error("[SAGA COMPENSATION ERROR] Cannot find product: {} to compensate!", event.getProductCode());
            return InventoryResponse.builder()
                    .success(false)
                    .productCode(event.getProductCode())
                    .message("Product not found during compensation")
                    .handledByInstance(serverPort)
                    .build();
        }

        int beforeStock = product.getStockQuantity();
        product.setStockQuantity(beforeStock + event.getQuantity());
        productRepository.save(product);

        log.info("[SAGA COMPENSATION COMPLETE] Stock successfully RESTORED for Product: {}. Before: {}, Restored: +{}, Current: {}",
                product.getProductCode(), beforeStock, event.getQuantity(), product.getStockQuantity());

        return InventoryResponse.builder()
                .success(true)
                .productCode(product.getProductCode())
                .remainingStock(product.getStockQuantity())
                .message("Stock compensated successfully")
                .handledByInstance(serverPort)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private ProductDto mapToDto(Product p) {
        return ProductDto.builder()
                .id(p.getId())
                .productCode(p.getProductCode())
                .name(p.getName())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                .description(p.getDescription())
                .sourceInstance("inventory-service:" + serverPort)
                .build();
    }
}
