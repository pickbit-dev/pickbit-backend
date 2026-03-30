package com.pickbit.productservice.domain.product.repository;

import com.pickbit.productservice.domain.product.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"category", "images"})
    Optional<Product> findDetailById(Long id);
}
