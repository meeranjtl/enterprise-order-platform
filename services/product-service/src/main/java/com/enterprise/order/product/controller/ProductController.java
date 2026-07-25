package com.enterprise.order.product.controller;

import com.enterprise.order.product.dto.ProductDTO;
import com.enterprise.order.product.dto.StockAdjustmentDTO;
import com.enterprise.order.product.service.ProductService;
import com.enterprise.order.shared.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management APIs")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product")
    public ResponseEntity<BaseResponse<ProductDTO>> createProduct(@Valid @RequestBody ProductDTO productDTO) {
        log.info("POST /api/v1/products - Creating product");

        ProductDTO created = productService.createProduct(productDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(created, "Product created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<BaseResponse<ProductDTO>> getProduct(@PathVariable("id") Long id) {
        log.info("GET /api/v1/products/{} - Fetching product", id);

        ProductDTO product = productService.getProduct(id);
        return ResponseEntity.ok(BaseResponse.success(product, "Product retrieved successfully"));
    }

    @GetMapping
    @Operation(summary = "Get all products with pagination")
    public ResponseEntity<BaseResponse<Page<ProductDTO>>> getAllProducts(Pageable pageable) {
        log.info("GET /api/v1/products - Fetching all products");

        Page<ProductDTO> products = productService.getAllProducts(pageable);
        return ResponseEntity.ok(BaseResponse.success(products, "Products retrieved successfully"));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products with catalog filters")
    public ResponseEntity<BaseResponse<Page<ProductDTO>>> searchProducts(
            @Parameter(description = "Filter by SKU") @RequestParam(name = "sku", required = false) String sku,
            @Parameter(description = "Filter by name") @RequestParam(name = "name", required = false) String name,
            @Parameter(description = "Filter by category ID") @RequestParam(name = "categoryId", required = false) Long categoryId,
            @Parameter(description = "Minimum price") @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price") @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @Parameter(description = "Filter by status (ACTIVE, INACTIVE, OUT_OF_STOCK, DISCONTINUED)") @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Only products with stock greater than zero") @RequestParam(name = "inStockOnly", required = false) Boolean inStockOnly,
            Pageable pageable) {
        log.info("GET /api/v1/products/search - Searching products");

        Page<ProductDTO> products = productService.searchProducts(
                sku, name, categoryId, minPrice, maxPrice, status, inStockOnly, pageable);
        return ResponseEntity.ok(BaseResponse.success(products, "Products found successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product")
    public ResponseEntity<BaseResponse<ProductDTO>> updateProduct(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody ProductDTO productDTO) {
        log.info("PUT /api/v1/products/{} - Updating product", id);

        ProductDTO updated = productService.updateProduct(id, productDTO);
        return ResponseEntity.ok(BaseResponse.success(updated, "Product updated successfully"));
    }

    @PatchMapping("/{id}/stock")
    @Operation(summary = "Update product stock quantity")
    public ResponseEntity<BaseResponse<ProductDTO>> updateStock(@PathVariable("id") Long id,
                                                                 @Valid @RequestBody StockAdjustmentDTO stockAdjustmentDTO) {
        log.info("PATCH /api/v1/products/{}/stock - Updating product stock", id);

        ProductDTO updated = productService.updateStock(id, stockAdjustmentDTO.getStockQuantity());
        return ResponseEntity.ok(BaseResponse.success(updated, "Product stock updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Discontinue product")
    public ResponseEntity<BaseResponse<Void>> deleteProduct(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/products/{} - Discontinuing product", id);

        productService.deleteProduct(id);
        return ResponseEntity.ok(BaseResponse.success(null, "Product discontinued successfully"));
    }
}