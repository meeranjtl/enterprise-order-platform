package com.enterprise.order.product.controller;

import com.enterprise.order.product.dto.ProductDTO;
import com.enterprise.order.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private ProductDTO productDTO;

    @BeforeEach
    void setUp() {
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
    void createProduct_success() throws Exception {
        when(productService.createProduct(any())).thenReturn(productDTO);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sku", is("SKU-100")));
    }

    @Test
    void getProduct_success() throws Exception {
        when(productService.getProduct(10L)).thenReturn(productDTO);

        mockMvc.perform(get("/api/v1/products/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(10)))
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void getAllProducts_success() throws Exception {
        Page<ProductDTO> page = new PageImpl<>(List.of(productDTO));
        when(productService.getAllProducts(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void searchProducts_success() throws Exception {
        Page<ProductDTO> page = new PageImpl<>(List.of(productDTO));
        when(productService.searchProducts(eq(null), eq("keyboard"), eq(1L), any(), any(), eq("ACTIVE"), eq(true), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search")
                .param("name", "keyboard")
                .param("categoryId", "1")
                .param("minPrice", "10.00")
                .param("maxPrice", "100.00")
                .param("status", "ACTIVE")
                .param("inStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void updateProduct_success() throws Exception {
        when(productService.updateProduct(eq(10L), any())).thenReturn(productDTO);

        mockMvc.perform(put("/api/v1/products/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku", is("SKU-100")));
    }

    @Test
    void updateStock_success() throws Exception {
        ProductDTO updated = ProductDTO.builder()
                .id(10L)
                .sku("SKU-100")
                .name("Wireless Keyboard")
                .price(new BigDecimal("49.99"))
                .stockQuantity(0)
                .status("OUT_OF_STOCK")
                .categoryId(1L)
                .build();
        when(productService.updateStock(10L, 0)).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/products/10/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockQuantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("OUT_OF_STOCK")));
    }

    @Test
    void deleteProduct_success() throws Exception {
        doNothing().when(productService).deleteProduct(10L);

        mockMvc.perform(delete("/api/v1/products/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}