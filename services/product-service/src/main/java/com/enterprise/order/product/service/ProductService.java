package com.enterprise.order.product.service;

import com.enterprise.order.product.dto.ProductDTO;
import com.enterprise.order.product.entity.Category;
import com.enterprise.order.product.entity.Product;
import com.enterprise.order.product.mapper.ProductMapper;
import com.enterprise.order.product.repository.ProductRepository;
import com.enterprise.order.product.specification.ProductSpecification;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.exception.ConflictException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductMapper productMapper;

    public ProductDTO createProduct(ProductDTO productDTO) {
        log.info("Creating product with SKU: {}", productDTO.getSku());

        if (productRepository.existsBySku(productDTO.getSku())) {
            throw new ConflictException("Product with SKU " + productDTO.getSku() + " already exists");
        }

        Category category = categoryService.findCategory(productDTO.getCategoryId());
        if (!category.isActive()) {
            throw new BadRequestException("Product category must be active");
        }

        Product product = productMapper.toEntity(productDTO);
        product.setCategory(category);
        product.setStatus(resolveStatus(productDTO.getStatus(), product.getStockQuantity()));

        Product savedProduct = productRepository.save(product);
        log.info("Product created with id: {}", savedProduct.getId());

        return productMapper.toDTO(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductDTO getProduct(Long id) {
        return productMapper.toDTO(findProduct(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(productMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> searchProducts(String sku,
                                           String name,
                                           Long categoryId,
                                           BigDecimal minPrice,
                                           BigDecimal maxPrice,
                                           String status,
                                           Boolean inStockOnly,
                                           Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Minimum price cannot be greater than maximum price");
        }

        Specification<Product> spec = Specification
                .where(ProductSpecification.skuContains(sku))
                .and(ProductSpecification.nameContains(name))
                .and(ProductSpecification.categoryEquals(categoryId))
                .and(ProductSpecification.minPrice(minPrice))
                .and(ProductSpecification.maxPrice(maxPrice))
                .and(ProductSpecification.statusEquals(status))
                .and(ProductSpecification.inStockOnly(inStockOnly));

        return productRepository.findAll(spec, pageable).map(productMapper::toDTO);
    }

    public ProductDTO updateProduct(Long id, ProductDTO productDTO) {
        log.info("Updating product with id: {}", id);

        Product product = findProduct(id);
        productRepository.findBySku(productDTO.getSku())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Product with SKU " + productDTO.getSku() + " already exists");
                });

        Category category = categoryService.findCategory(productDTO.getCategoryId());
        if (!category.isActive()) {
            throw new BadRequestException("Product category must be active");
        }

        product.setSku(productDTO.getSku());
        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setPrice(productDTO.getPrice());
        product.setStockQuantity(productDTO.getStockQuantity());
        product.setCategory(category);
        product.setStatus(resolveStatus(productDTO.getStatus(), productDTO.getStockQuantity()));

        return productMapper.toDTO(productRepository.save(product));
    }

    public ProductDTO updateStock(Long id, Integer stockQuantity) {
        log.info("Updating stock for product id: {} to {}", id, stockQuantity);

        Product product = findProduct(id);
        product.setStockQuantity(stockQuantity);
        if (product.getStatus() == Product.ProductStatus.OUT_OF_STOCK && stockQuantity > 0) {
            product.setStatus(Product.ProductStatus.ACTIVE);
        } else if (product.getStatus() == Product.ProductStatus.ACTIVE && stockQuantity == 0) {
            product.setStatus(Product.ProductStatus.OUT_OF_STOCK);
        }

        return productMapper.toDTO(productRepository.save(product));
    }

    public void deleteProduct(Long id) {
        log.info("Discontinuing product with id: {}", id);

        Product product = findProduct(id);
        product.setStatus(Product.ProductStatus.DISCONTINUED);
        productRepository.save(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
    }

    private Product.ProductStatus resolveStatus(String requestedStatus, Integer stockQuantity) {
        if (stockQuantity == null || stockQuantity < 0) {
            throw new BadRequestException("Stock quantity cannot be negative");
        }

        Product.ProductStatus status;
        try {
            status = requestedStatus == null || requestedStatus.isBlank()
                    ? Product.ProductStatus.ACTIVE
                    : Product.ProductStatus.valueOf(requestedStatus.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unsupported product status: " + requestedStatus);
        }

        if (status == Product.ProductStatus.ACTIVE && stockQuantity == 0) {
            return Product.ProductStatus.OUT_OF_STOCK;
        }

        return status;
    }
}