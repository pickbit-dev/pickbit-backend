package com.pickbit.productservice.domain.category.repository;

import com.pickbit.productservice.domain.category.entity.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @EntityGraph(attributePaths = {"parentCategory"})
    Optional<Category> findDetailById(Long id);
}
