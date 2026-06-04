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

import java.util.List;
import java.util.Set;

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

        long total = count(builder);
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

        long total = count(builder);
        return new PageImpl<>(content, pageable, total);
    }

    private BooleanBuilder buildSearchCondition(ProductSearchCondition condition) {
        QProduct product = getQEntity();
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(product.productStatus.ne(ProductStatus.DELETED));

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
