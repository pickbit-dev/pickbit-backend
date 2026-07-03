package com.pickbit.productservice.infrastructure.persistence;

import com.pickbit.productservice.domain.Category;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByName(String name);

    Optional<Category> findByName(String name);

    List<Category> findAllByActiveTrue(Sort sort);

    @Query("select coalesce(max(c.sortOrder), 0) from Category c")
    Integer findMaxSortOrder();
}
