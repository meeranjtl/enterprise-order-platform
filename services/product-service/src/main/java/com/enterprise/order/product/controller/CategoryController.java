package com.enterprise.order.product.controller;

import com.enterprise.order.product.dto.CategoryDTO;
import com.enterprise.order.product.service.CategoryService;
import com.enterprise.order.shared.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category management APIs")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create a new category")
    public ResponseEntity<BaseResponse<CategoryDTO>> createCategory(@Valid @RequestBody CategoryDTO categoryDTO) {
        log.info("POST /api/v1/categories - Creating category");

        CategoryDTO created = categoryService.createCategory(categoryDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(created, "Category created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID")
    public ResponseEntity<BaseResponse<CategoryDTO>> getCategory(@PathVariable("id") Long id) {
        log.info("GET /api/v1/categories/{} - Fetching category", id);

        CategoryDTO category = categoryService.getCategory(id);
        return ResponseEntity.ok(BaseResponse.success(category, "Category retrieved successfully"));
    }

    @GetMapping
    @Operation(summary = "Get all categories with pagination")
    public ResponseEntity<BaseResponse<Page<CategoryDTO>>> getAllCategories(Pageable pageable) {
        log.info("GET /api/v1/categories - Fetching all categories");

        Page<CategoryDTO> categories = categoryService.getAllCategories(pageable);
        return ResponseEntity.ok(BaseResponse.success(categories, "Categories retrieved successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category")
    public ResponseEntity<BaseResponse<CategoryDTO>> updateCategory(@PathVariable("id") Long id,
                                                                     @Valid @RequestBody CategoryDTO categoryDTO) {
        log.info("PUT /api/v1/categories/{} - Updating category", id);

        CategoryDTO updated = categoryService.updateCategory(id, categoryDTO);
        return ResponseEntity.ok(BaseResponse.success(updated, "Category updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category")
    public ResponseEntity<BaseResponse<Void>> deleteCategory(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/categories/{} - Deleting category", id);

        categoryService.deleteCategory(id);
        return ResponseEntity.ok(BaseResponse.success(null, "Category deleted successfully"));
    }
}