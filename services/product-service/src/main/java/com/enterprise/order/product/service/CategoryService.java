package com.enterprise.order.product.service;

import com.enterprise.order.product.dto.CategoryDTO;
import com.enterprise.order.product.entity.Category;
import com.enterprise.order.product.entity.Product;
import com.enterprise.order.product.mapper.CategoryMapper;
import com.enterprise.order.product.repository.CategoryRepository;
import com.enterprise.order.product.repository.ProductRepository;
import com.enterprise.order.shared.exception.ConflictException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    public CategoryDTO createCategory(CategoryDTO categoryDTO) {
        log.info("Creating category with name: {}", categoryDTO.getName());

        if (categoryRepository.existsByNameIgnoreCase(categoryDTO.getName())) {
            throw new ConflictException("Category with name " + categoryDTO.getName() + " already exists");
        }

        Category category = categoryMapper.toEntity(categoryDTO);
        category.setActive(categoryDTO.getActive() == null || categoryDTO.getActive());

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created with id: {}", savedCategory.getId());

        return categoryMapper.toDTO(savedCategory);
    }

    @Transactional(readOnly = true)
    public CategoryDTO getCategory(Long id) {
        return categoryMapper.toDTO(findCategory(id));
    }

    @Transactional(readOnly = true)
    public Page<CategoryDTO> getAllCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(categoryMapper::toDTO);
    }

    public CategoryDTO updateCategory(Long id, CategoryDTO categoryDTO) {
        log.info("Updating category with id: {}", id);

        Category category = findCategory(id);
        categoryRepository.findByNameIgnoreCase(categoryDTO.getName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Category with name " + categoryDTO.getName() + " already exists");
                });

        category.setName(categoryDTO.getName());
        category.setDescription(categoryDTO.getDescription());
        if (categoryDTO.getActive() != null) {
            category.setActive(categoryDTO.getActive());
        }

        return categoryMapper.toDTO(categoryRepository.save(category));
    }

    public void deleteCategory(Long id) {
        log.info("Deleting category with id: {}", id);

        Category category = findCategory(id);
        if (productRepository.existsByCategoryAndStatusNot(category, Product.ProductStatus.DISCONTINUED)) {
            throw new ConflictException("Category cannot be deleted while active products are assigned to it");
        }

        categoryRepository.delete(category);
    }

    Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id.toString()));
    }
}