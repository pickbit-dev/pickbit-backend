package com.pickbit.productservice.infrastructure.persistence;

import com.pickbit.productservice.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * 모아둔 조회수를 한 번에 더합니다.
     *
     * <p>엔티티를 읽어서 더하지 않고 바로 UPDATE 를 날립니다. 조회수는 다른 필드와 함께
     * 갱신될 필요가 없고, 엔티티를 거치면 불필요한 SELECT 와 버전 충돌이 생깁니다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.viewCount = p.viewCount + :delta WHERE p.id = :id")
    int addViewCount(@Param("id") Long id, @Param("delta") long delta);
}
