package com.enterprise.order.product.service;

import com.enterprise.order.product.dto.ProductDTO;
import com.enterprise.order.product.entity.Category;
import com.enterprise.order.product.entity.Product;
import com.enterprise.order.product.mapper.ProductMapper;
import com.enterprise.order.product.repository.ProductRepository;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.exception.ConflictException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private Category category;
    private Product product;
    private ProductDTO productDTO;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(1L)
                .name("Electronics")
                .active(true)
                .build();

        product = Product.builder()
                .id(10L)
                .sku("SKU-100")
                .name("Wireless Keyboard")
                .description("Compact keyboard")
                .price(new BigDecimal("49.99"))
                .stockQuantity(25)
                .status(Product.ProductStatus.ACTIVE)
                .category(category)
                .build();

        productDTO = ProductDTO.builder()
                .id(10L)
                .sku("SKU-100")
                .name("Wireless Keyboard")
                .description("Compact keyboard")
                .price(new BigDecimal("49.99"))
                .stockQuantity(25)
                .status("ACTIVE")
                .categoryId(1L)
                .categoryName("Electronics")
                .build();
    }

    @Test
    void createProduct_success() {
        when(productRepository.existsBySku("SKU-100")).thenReturn(false);
        when(categoryService.findCategory(1L)).thenReturn(category);
        when(productMapper.toEntity(productDTO)).thenReturn(product);
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toDTO(product)).thenReturn(productDTO);

        ProductDTO result = productService.createProduct(productDTO);

        assertNotNull(result);
        assertEquals("SKU-100", result.getSku());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void createProduct_duplicateSku() {
        when(productRepository.existsBySku("SKU-100")).thenReturn(true);

        assertThrows(ConflictException.class, () -> productService.createProduct(productDTO));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_inactiveCategoryRejected() {
        category.setActive(false);
        when(productRepository.existsBySku("SKU-100")).thenReturn(false);
        when(categoryService.findCategory(1L)).thenReturn(category);

        assertThrows(BadRequestException.class, () -> productService.createProduct(productDTO));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getProduct_notFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProduct(999L));
    }

    @Test
    void searchProducts_invalidPriceRange() {
        Pageable pageable = PageRequest.of(0, 10);

        assertThrows(BadRequestException.class, () -> productService.searchProducts(
                null, null, null, new BigDecimal("50.00"), new BigDecimal("10.00"), null, null, pageable));
    }

    @Test
    void getAllProducts_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
        when(productRepository.findAll(pageable)).thenReturn(page);
        when(productMapper.toDTO(product)).thenReturn(productDTO);

        Page<ProductDTO> result = productService.getAllProducts(pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void updateStock_setsOutOfStockWhenActiveProductReachesZero() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        ProductDTO outOfStockDTO = ProductDTO.builder().id(10L).sku("SKU-100").status("OUT_OF_STOCK").stockQuantity(0).build();
        when(productMapper.toDTO(product)).thenReturn(outOfStockDTO);

        ProductDTO result = productService.updateStock(10L, 0);

        assertEquals("OUT_OF_STOCK", result.getStatus());
        assertEquals(Product.ProductStatus.OUT_OF_STOCK, product.getStatus());
    }

    @Test
    void deleteProduct_softDeletes() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        productService.deleteProduct(10L);

        assertEquals(Product.ProductStatus.DISCONTINUED, product.getStatus());
        verify(productRepository, times(1)).save(product);
    }
}