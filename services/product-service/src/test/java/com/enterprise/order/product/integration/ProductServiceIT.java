package com.enterprise.order.product.integration;

import com.enterprise.order.product.dto.CategoryDTO;
import com.enterprise.order.product.dto.ProductDTO;
import com.enterprise.order.product.entity.Product;
import com.enterprise.order.product.repository.CategoryRepository;
import com.enterprise.order.product.repository.ProductRepository;
import com.enterprise.order.product.service.CategoryService;
import com.enterprise.order.product.service.ProductService;
import com.enterprise.order.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProductServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("enterprise_order_product_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Mirror the Docker Compose URL (?currentSchema=product) so unqualified table
        // names resolve in the service schema for Hibernate's ddl-auto: validate.
        registry.add("spring.datasource.url", () -> {
            String url = postgres.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=product";
        });
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private CategoryDTO category;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        category = categoryService.createCategory(CategoryDTO.builder()
                .name("Electronics")
                .description("Devices and accessories")
                .active(true)
                .build());
    }

    @Test
    void createProduct_integration() {
        ProductDTO created = productService.createProduct(productRequest("SKU-IT-100", "Wireless Keyboard", "49.99", 25));

        assertNotNull(created.getId());
        assertEquals("SKU-IT-100", created.getSku());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(category.getId(), created.getCategoryId());
    }

    @Test
    void createProduct_duplicateSkuRejected() {
        productService.createProduct(productRequest("SKU-IT-101", "Mouse", "19.99", 10));

        assertThrows(ConflictException.class,
                () -> productService.createProduct(productRequest("SKU-IT-101", "Mouse Pro", "29.99", 10)));
    }

    @Test
    void searchProducts_filtersByNameAndPrice() {
        productService.createProduct(productRequest("SKU-IT-102", "Wireless Keyboard", "49.99", 25));
        productService.createProduct(productRequest("SKU-IT-103", "Laptop Stand", "39.99", 0));

        Page<ProductDTO> results = productService.searchProducts(
                null,
                "keyboard",
                category.getId(),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                "ACTIVE",
                true,
                PageRequest.of(0, 10));

        assertEquals(1, results.getTotalElements());
        assertEquals("SKU-IT-102", results.getContent().get(0).getSku());
    }

    @Test
    void updateStock_movesActiveProductToOutOfStock() {
        ProductDTO created = productService.createProduct(productRequest("SKU-IT-104", "USB Hub", "24.99", 5));

        ProductDTO updated = productService.updateStock(created.getId(), 0);

        assertEquals(0, updated.getStockQuantity());
        assertEquals("OUT_OF_STOCK", updated.getStatus());
    }

    @Test
    void deleteProduct_marksProductDiscontinued() {
        ProductDTO created = productService.createProduct(productRequest("SKU-IT-105", "Monitor Arm", "89.99", 3));

        productService.deleteProduct(created.getId());

        Product product = productRepository.findById(created.getId()).orElseThrow();
        assertEquals(Product.ProductStatus.DISCONTINUED, product.getStatus());
        assertFalse(productRepository.findById(created.getId()).isEmpty());
    }

    private ProductDTO productRequest(String sku, String name, String price, int stockQuantity) {
        return ProductDTO.builder()
                .sku(sku)
                .name(name)
                .description(name + " description")
                .price(new BigDecimal(price))
                .stockQuantity(stockQuantity)
                .categoryId(category.getId())
                .build();
    }
}