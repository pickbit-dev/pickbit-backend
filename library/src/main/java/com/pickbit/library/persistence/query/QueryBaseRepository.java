package com.pickbit.library.persistence.query;//package com.example.wsa_backend_library.repository;


import com.pickbit.library.persistence.entity.BaseEntity;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Objects;
import java.util.Set;


@NoRepositoryBean
@RequiredArgsConstructor
@Slf4j
public abstract class QueryBaseRepository<T extends BaseEntity, Q extends EntityPathBase<T>> {

    protected final JPAQueryFactory queryFactory;

    protected abstract Q getQEntity();

    /**
     * BooleanBuilder + 페이징으로 데이터 조회 (Spring Data Sort 지원)
     */
    public Page<T> findPage(BooleanBuilder builder, Pageable pageable) {
        Q qEntity = getQEntity();

        JPAQuery<T> query = queryFactory.selectFrom(qEntity);

        if (builder != null && builder.hasValue()) {
            query.where(builder);
        }

        applySorting(query, qEntity, pageable);

        long total = count(builder);

        List<T> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * BooleanBuilder + 페이징 + 커스텀 정렬 (OrderSpecifier 우선)
     */
    public Page<T> findPage(BooleanBuilder builder, Pageable pageable, OrderSpecifier<?>... orders) {
        Q qEntity = getQEntity();

        JPAQuery<T> query = queryFactory.selectFrom(qEntity);

        if (builder != null && builder.hasValue()) {
            query.where(builder);
        }

        if (orders != null && orders.length > 0) {
            query.orderBy(orders);
        } else {
            applySorting(query, qEntity, pageable);
        }

        long total = count(builder);

        List<T> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * BooleanBuilder로 전체 데이터 조회
     */
    public List<T> findAll(BooleanBuilder builder) {
        Q qEntity = getQEntity();

        JPAQuery<T> query = queryFactory.selectFrom(qEntity);

        if (builder != null && builder.hasValue()) {
            query.where(builder);
        }

        return query.fetch();
    }

    /**
     * BooleanBuilder로 개수 조회
     */
    public long count(BooleanBuilder builder) {
        Q qEntity = getQEntity();

        JPAQuery<Long> countQuery = queryFactory
                .select(qEntity.count())
                .from(qEntity);

        if (builder != null && builder.hasValue()) {
            countQuery.where(builder);
        }

        Long result = countQuery.fetchOne();
        return result != null ? result : 0L;
    }

    /**
     * 정렬 필드명으로부터 OrderSpecifier를 생성한다.
     */
    protected OrderSpecifier<?> resolveOrderSpecifier(String field, Order direction) {
        PathBuilder<T> entityPath = new PathBuilder<>(getQEntity().getType(), getQEntity().toString());
        return new OrderSpecifier<>(direction, entityPath.getComparable(field, Comparable.class));
    }

    /**
     * Spring Data Sort를 QueryDSL OrderSpecifier 배열로 변환
     */
    public <E> OrderSpecifier<?>[] createOrderSpecifiers(Class<E> entityClass, String entityAlias, Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier<?>[0];
        }

        Set<String> sortableFields = getSortableFields();

        return pageable.getSort().stream()
                .map(order -> {
                    String property = order.getProperty();

                    if (!sortableFields.contains(property)) {
                        log.warn("허용되지 않은 정렬 필드 무시: {}", property);
                        return null;
                    }

                    try {
                        Order direction = order.isAscending() ? Order.ASC : Order.DESC;
                        return resolveOrderSpecifier(property, direction);
                    } catch (Exception e) {
                        log.warn("정렬 적용 실패 - 필드: {}, 오류: {}", property, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(OrderSpecifier[]::new);
    }

    /**
     * Spring Data Sort를 QueryDSL OrderSpecifier로 변환 후 적용
     */
    public void applySorting(JPAQuery<?> query, Q qEntity, Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return;
        }

        OrderSpecifier<?>[] orders = createOrderSpecifiers(qEntity.getType(), qEntity.toString(), pageable);

        if (orders.length > 0) {
            query.orderBy(orders);
        }
    }

    /**
     * 정렬 가능한 필드 목록 반환 (하위 클래스에서 오버라이드)
     */
    protected Set<String> getSortableFields() {
        return Set.of("id", "createdDate", "updatedDate");
    }

    /**
     * Predicate + 페이징 (호환성)
     */
    public Page<T> findPage(Predicate condition, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        if (condition != null) {
            builder.and(condition);
        }
        return findPage(builder, pageable);
    }

    /**
     * Predicate로 전체 조회 (호환성)
     */
    public List<T> findAll(Predicate condition) {
        BooleanBuilder builder = new BooleanBuilder();
        if (condition != null) {
            builder.and(condition);
        }
        return findAll(builder);
    }

    /**
     * Predicate로 개수 조회 (호환성)
     */
    public long count(Predicate condition) {
        BooleanBuilder builder = new BooleanBuilder();
        if (condition != null) {
            builder.and(condition);
        }
        return count(builder);
    }

    /**
     * 전체 페이징 조회 (조건 없음)
     */
    public Page<T> findAll(Pageable pageable) {
        return findPage(new BooleanBuilder(), pageable);
    }

    /**
     * ID 기반 커서 페이징 (무한 스크롤용)
     */
    public List<T> findByIdCursor(Long lastId, int limit, BooleanBuilder builder) {
        Q qEntity = getQEntity();

        JPAQuery<T> query = queryFactory.selectFrom(qEntity);

        if (builder != null && builder.hasValue()) {
            query.where(builder);
        }

        if (lastId != null) {
            PathBuilder<T> pathBuilder = new PathBuilder<>(qEntity.getType(), qEntity.toString());
            query.where(pathBuilder.getNumber("id", Long.class).lt(lastId));
        }

        return query
                .orderBy(new OrderSpecifier<>(Order.DESC,
                        new PathBuilder<>(qEntity.getType(), qEntity.toString()).getNumber("id", Long.class)))
                .limit(limit)
                .fetch();
    }

    /**
     * ID 기반 커서 페이징 (조건 없음)
     */
    public List<T> findByIdCursor(Long lastId, int limit) {
        return findByIdCursor(lastId, limit, null);
    }
}