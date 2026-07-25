package com.enterprise.order.product.repository;

import com.enterprise.order.product.entity.Category;
import com.enterprise.order.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsByCategoryAndStatusNot(Category category, Product.ProductStatus status);
}