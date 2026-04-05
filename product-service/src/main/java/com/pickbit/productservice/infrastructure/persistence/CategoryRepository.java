package com.pickbit.productservice.infrastructure.persistence;

import com.pickbit.productservice.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}