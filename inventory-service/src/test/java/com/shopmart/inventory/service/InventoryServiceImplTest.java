package com.shopmart.inventory.service;

import com.shopmart.common.dto.InventoryDeductRequest;
import com.shopmart.common.dto.InventoryResponse;
import com.shopmart.common.dto.ProductDto;
import com.shopmart.common.event.InventoryCompensateEvent;
import com.shopmart.inventory.entity.Product;
import com.shopmart.inventory.repository.ProductRepository;
import com.shopmart.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryService, "serverPort", "8082");
        testProduct = Product.builder()
                .id(1L)
                .productCode("PROD-001")
                .name("iPhone 15 Pro Max")
                .price(new BigDecimal("30000000"))
                .stockQuantity(50)
                .description("Flagship smartphone")
                .build();
    }

    @Test
    @DisplayName("Câu 4 Test: Query product by code - Returns valid ProductDto")
    void testGetProductByCode_Success() {
        when(productRepository.findByProductCode("PROD-001")).thenReturn(Optional.of(testProduct));

        ProductDto dto = inventoryService.getProductByCode("PROD-001");

        assertNotNull(dto);
        assertEquals("PROD-001", dto.getProductCode());
        assertEquals(50, dto.getStockQuantity());
        verify(productRepository, times(1)).findByProductCode("PROD-001");
    }

    @Test
    @DisplayName("Câu 2/3 Test: Deduct stock succeeds when quantity is sufficient")
    void testDeductStock_Success() {
        when(productRepository.findByProductCode("PROD-001")).thenReturn(Optional.of(testProduct));

        InventoryDeductRequest request = InventoryDeductRequest.builder()
                .orderId(10L)
                .productCode("PROD-001")
                .quantity(5)
                .build();

        InventoryResponse response = inventoryService.deductStock(request);

        assertTrue(response.isSuccess());
        assertEquals(45, response.getRemainingStock());
        verify(productRepository, times(1)).save(testProduct);
    }

    @Test
    @DisplayName("Câu 2/3 Test: Deduct stock fails gracefully when stock is insufficient")
    void testDeductStock_InsufficientStock() {
        testProduct.setStockQuantity(2);
        when(productRepository.findByProductCode("PROD-001")).thenReturn(Optional.of(testProduct));

        InventoryDeductRequest request = InventoryDeductRequest.builder()
                .orderId(11L)
                .productCode("PROD-001")
                .quantity(10)
                .build();

        InventoryResponse response = inventoryService.deductStock(request);

        assertFalse(response.isSuccess());
        assertEquals(2, response.getRemainingStock());
        assertTrue(response.getMessage().contains("Insufficient stock"));
        verify(productRepository, never()).save(testProduct);
    }

    @Test
    @DisplayName("Câu 3 Test: Saga Compensating Rollback - Restores stock after downstream payment failure")
    void testSagaCompensateStock_RollbackSuccess() {
        testProduct.setStockQuantity(40); // Was deducted earlier
        when(productRepository.findByProductCode("PROD-001")).thenReturn(Optional.of(testProduct));

        InventoryCompensateEvent event = InventoryCompensateEvent.builder()
                .orderId(10L)
                .productCode("PROD-001")
                .quantity(10)
                .reason("Downstream payment failed - Saga Rollback")
                .build();

        InventoryResponse response = inventoryService.compensateStock(event);

        assertTrue(response.isSuccess());
        assertEquals(50, testProduct.getStockQuantity()); // 40 + 10 = 50
        assertEquals(50, response.getRemainingStock());
        verify(productRepository, times(1)).save(testProduct);
    }
}
