package com.enterprise.order.product.mapper;

import com.enterprise.order.product.dto.ProductDTO;
import com.enterprise.order.product.entity.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    default ProductDTO toDTO(Product product) {
        if (product == null) {
            return null;
        }

        return ProductDTO.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    default Product toEntity(ProductDTO dto) {
        if (dto == null) {
            return null;
        }

        return Product.builder()
                .id(dto.getId())
                .sku(dto.getSku())
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .stockQuantity(dto.getStockQuantity())
                .build();
    }
}