package com.enterprise.order.product.specification;

import com.enterprise.order.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> skuContains(String sku) {
        return (root, query, criteriaBuilder) ->
                sku == null || sku.isBlank() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), "%" + sku.toLowerCase() + "%");
    }

    public static Specification<Product> nameContains(String name) {
        return (root, query, criteriaBuilder) ->
                name == null || name.isBlank() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Product> categoryEquals(Long categoryId) {
        return (root, query, criteriaBuilder) ->
                categoryId == null ? null : criteriaBuilder.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> minPrice(BigDecimal minPrice) {
        return (root, query, criteriaBuilder) ->
                minPrice == null ? null : criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> maxPrice(BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) ->
                maxPrice == null ? null : criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> statusEquals(String status) {
        return (root, query, criteriaBuilder) ->
                status == null || status.isBlank() ? null :
                        criteriaBuilder.equal(root.get("status"), Product.ProductStatus.valueOf(status.toUpperCase()));
    }

    public static Specification<Product> inStockOnly(Boolean inStockOnly) {
        return (root, query, criteriaBuilder) ->
                Boolean.TRUE.equals(inStockOnly) ? criteriaBuilder.greaterThan(root.get("stockQuantity"), 0) : null;
    }
}