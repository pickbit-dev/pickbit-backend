package com.pickbit.productservice.infrastructure.persistence;

import com.pickbit.productservice.domain.Product;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByProductStatusNot(ProductStatus status, Pageable pageable);

    Page<Product> findByProductStatusNotAndCategoryId(ProductStatus status, Long categoryId, Pageable pageable);
}