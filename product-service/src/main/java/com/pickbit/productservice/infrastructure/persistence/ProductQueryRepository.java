package com.pickbit.productservice.infrastructure.persistence;

import com.pickbit.library.persistence.query.QueryBaseRepository;
import com.pickbit.productservice.api.dto.request.ProductSearchCondition;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.domain.Product;
import com.pickbit.productservice.domain.QCategory;
import com.pickbit.productservice.domain.QProduct;
import com.pickbit.productservice.domain.QProductImage;
import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductQueryRepository extends QueryBaseRepository<Product, QProduct> {

    public ProductQueryRepository(JPAQueryFactory queryFactory) {
        super(queryFactory);
    }

    @Override
    protected QProduct getQEntity() {
        return QProduct.product;
    }

    @Override
    protected Set<String> getSortableFields() {
        return Set.of("id", "createdDate", "updatedDate", "startingPrice");
    }

    /**
     * 목록 조회의 전체 건수를 짧게 캐싱합니다.
     *
     * <p>부하 테스트에서 드러난 목록 API 의 지배적 비용이 이 카운트였습니다. 상품 71,750건
     * 기준으로 한 번에 25.7ms 가 걸렸고, 목록 요청 1,014,383회 동안 누적 18,796초를 썼습니다.
     * 본문 쿼리는 {@code LIMIT 20} 에서 조기 종료돼 0.04ms 로 끝납니다. 느린 건 카운트뿐입니다.
     *
     * <p>인덱스로는 25.7ms → 23.0ms 정도밖에 못 줄입니다. {@code product_status != 'DELETED'}
     * 가 사실상 전 구간을 읽기 때문입니다. 결국 "매 요청마다 세지 않는" 것이 답입니다.
     *
     * <p>대신 <b>전체 건수가 최대 {@value #COUNT_CACHE_TTL_SECONDS}초까지 실제와 어긋날 수
     * 있습니다.</b> 목록의 "총 N개" 표시에만 쓰이는 값이라 감수할 만한 오차로 봅니다.
     * 페이지 본문은 캐싱하지 않으므로 매물 자체는 항상 최신입니다.
     */
    private static final long COUNT_CACHE_TTL_SECONDS = 10;

    /** 검색 조건 종류가 폭증해도 메모리를 잠식하지 않도록 상한을 둔다. */
    private static final int COUNT_CACHE_MAX_ENTRIES = 500;

    private record CachedCount(long value, long expiresAtNanos) {
    }

    private final Map<String, CachedCount> countCache = new ConcurrentHashMap<>();

    private long cachedCount(BooleanBuilder builder) {
        String key = String.valueOf(builder);
        long now = System.nanoTime();

        CachedCount hit = countCache.get(key);
        if (hit != null && hit.expiresAtNanos() > now) {
            return hit.value();
        }

        long value = count(builder);

        if (countCache.size() >= COUNT_CACHE_MAX_ENTRIES) {
            // 상한에 닿았을 때 만료된 항목을 걷어낸다. 이게 없으면 한 번 가득 찬 뒤로는
            // 만료된 쓰레기가 자리를 차지한 채 캐시가 영영 동작하지 않는다.
            countCache.values().removeIf(entry -> entry.expiresAtNanos() <= now);
        }
        if (countCache.size() < COUNT_CACHE_MAX_ENTRIES) {
            countCache.put(key, new CachedCount(value, now + Duration.ofSeconds(COUNT_CACHE_TTL_SECONDS).toNanos()));
        }
        return value;
    }

    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        BooleanBuilder builder = buildSearchCondition(condition);
        return findPage(builder, pageable);
    }

    public Page<ProductSummaryResponse> searchSummary(ProductSearchCondition condition, Pageable pageable) {
        QProduct prod = getQEntity();
        QCategory cat = QCategory.category;
        QProductImage img = QProductImage.productImage;

        BooleanBuilder builder = buildSearchCondition(condition);

        JPAQuery<ProductSummaryResponse> query = queryFactory
                .select(Projections.constructor(ProductSummaryResponse.class,
                        prod.id,
                        prod.name,
                        prod.startingPrice,
                        prod.productStatus,
                        prod.productCondition,
                        prod.sellerUserId,
                        prod.sellerNickname,
                        cat.name,
                        JPAExpressions
                                .select(img.imageUrl)
                                .from(img)
                                .where(img.product.eq(prod)
                                        .and(img.imageType.eq(ImageType.THUMBNAIL)))
                                .orderBy(img.sortOrder.asc())
                                .limit(1),
                        prod.createdDate
                ))
                .from(prod)
                .leftJoin(prod.category, cat)
                .where(builder);

        applySorting(query, prod, pageable);

        List<ProductSummaryResponse> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = cachedCount(builder);
        return new PageImpl<>(content, pageable, total);
    }

    public Page<ProductSummaryResponse> searchSellingProducts(Long sellerUserId, ProductStatus status, Pageable pageable) {
        QProduct prod = getQEntity();
        QCategory cat = QCategory.category;
        QProductImage img = QProductImage.productImage;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(prod.productStatus.ne(ProductStatus.DELETED));
        builder.and(prod.sellerUserId.eq(sellerUserId));

        if (status != null) {
            builder.and(prod.productStatus.eq(status));
        }

        JPAQuery<ProductSummaryResponse> query = queryFactory
                .select(Projections.constructor(ProductSummaryResponse.class,
                        prod.id,
                        prod.name,
                        prod.startingPrice,
                        prod.productStatus,
                        prod.productCondition,
                        prod.sellerUserId,
                        prod.sellerNickname,
                        cat.name,
                        JPAExpressions
                                .select(img.imageUrl)
                                .from(img)
                                .where(img.product.eq(prod)
                                        .and(img.imageType.eq(ImageType.THUMBNAIL)))
                                .orderBy(img.sortOrder.asc())
                                .limit(1),
                        prod.createdDate
                ))
                .from(prod)
                .leftJoin(prod.category, cat)
                .where(builder);

        applySorting(query, prod, pageable);

        List<ProductSummaryResponse> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 내 판매목록은 판매자마다 조건이 달라 캐시 적중률이 낮고 항목 수만 늘린다.
        // idx_product_seller_status 로 좁혀지므로 매번 세도 부담이 적다.
        long total = count(builder);
        return new PageImpl<>(content, pageable, total);
    }

    private BooleanBuilder buildSearchCondition(ProductSearchCondition condition) {
        QProduct product = getQEntity();
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(product.productStatus.ne(ProductStatus.DELETED));

        if (condition.productStatus() != null && !condition.productStatus().isEmpty()) {
            builder.and(product.productStatus.in(condition.productStatus()));
        }

        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            builder.and(
                    product.name.containsIgnoreCase(condition.keyword())
                            .or(product.description.containsIgnoreCase(condition.keyword()))
            );
        }

        if (condition.categoryId() != null) {
            builder.and(product.category.id.eq(condition.categoryId()));
        }

        if (condition.sellerNickname() != null && !condition.sellerNickname().isBlank()) {
            builder.and(product.sellerNickname.eq(condition.sellerNickname()));
        }

        if (condition.minPrice() != null) {
            builder.and(product.startingPrice.goe(condition.minPrice()));
        }

        if (condition.maxPrice() != null) {
            builder.and(product.startingPrice.loe(condition.maxPrice()));
        }

        return builder;
    }
}
