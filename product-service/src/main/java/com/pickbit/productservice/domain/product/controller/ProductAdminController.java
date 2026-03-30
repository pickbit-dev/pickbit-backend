package com.pickbit.productservice.domain.product.controller;

import com.pickbit.productservice.domain.product.dto.request.CreateProductRequest;
import com.pickbit.productservice.domain.product.dto.request.UpdateProductRequest;
import com.pickbit.productservice.domain.product.dto.request.UpdateProductStatusRequest;
import com.pickbit.productservice.domain.product.dto.response.ProductDetailResponse;
import com.pickbit.productservice.domain.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/product/admin/products")
public class ProductAdminController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductDetailResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse get(@PathVariable Long productId) {
        return productService.get(productId);
    }

    @PatchMapping("/{productId}")
    public ProductDetailResponse update(@PathVariable Long productId, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(productId, request);
    }

    @PatchMapping("/{productId}/status")
    public ProductDetailResponse updateStatus(@PathVariable Long productId, @Valid @RequestBody UpdateProductStatusRequest request) {
        return productService.updateStatus(productId, request.status());
    }
}
