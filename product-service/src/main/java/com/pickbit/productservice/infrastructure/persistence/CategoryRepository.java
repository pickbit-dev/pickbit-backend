package com.pickbit.productservice.infrastructure.persistence;

import com.pickbit.productservice.domain.Category;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByName(String name);

    List<Category> findAllByActiveTrue(Sort sort);
}
