package com.enterprise.order.product.service;

import com.enterprise.order.product.dto.CategoryDTO;
import com.enterprise.order.product.entity.Category;
import com.enterprise.order.product.entity.Product;
import com.enterprise.order.product.mapper.CategoryMapper;
import com.enterprise.order.product.repository.CategoryRepository;
import com.enterprise.order.product.repository.ProductRepository;
import com.enterprise.order.shared.exception.ConflictException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;
    private CategoryDTO categoryDTO;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(1L)
                .name("Electronics")
                .description("Devices and accessories")
                .active(true)
                .build();

        categoryDTO = CategoryDTO.builder()
                .id(1L)
                .name("Electronics")
                .description("Devices and accessories")
                .active(true)
                .build();
    }

    @Test
    void createCategory_success() {
        when(categoryRepository.existsByNameIgnoreCase("Electronics")).thenReturn(false);
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(category);
        when(categoryRepository.save(category)).thenReturn(category);
        when(categoryMapper.toDTO(category)).thenReturn(categoryDTO);

        CategoryDTO result = categoryService.createCategory(categoryDTO);

        assertNotNull(result);
        assertEquals("Electronics", result.getName());
    }

    @Test
    void createCategory_duplicateName() {
        when(categoryRepository.existsByNameIgnoreCase("Electronics")).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.createCategory(categoryDTO));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void getCategory_notFound() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategory(999L));
    }

    @Test
    void deleteCategory_blocksActiveProducts() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryAndStatusNot(category, Product.ProductStatus.DISCONTINUED)).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.deleteCategory(1L));
        verify(categoryRepository, never()).delete(category);
    }

    @Test
    void deleteCategory_success() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryAndStatusNot(category, Product.ProductStatus.DISCONTINUED)).thenReturn(false);

        categoryService.deleteCategory(1L);

        verify(categoryRepository, times(1)).delete(category);
    }
}