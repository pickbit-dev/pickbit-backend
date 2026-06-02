package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.api.dto.request.PaymentSearchCondition;
import com.pickbit.paymentservice.api.dto.request.PaymentViewType;
import com.pickbit.paymentservice.api.dto.response.PaymentSummaryResponse;
import com.pickbit.paymentservice.domain.QPayment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PaymentQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<PaymentSummaryResponse> searchMyPayments(
            Long buyerUserId,
            PaymentSearchCondition condition,
            Pageable pageable
    ) {
        QPayment payment = QPayment.payment;
        BooleanBuilder builder = buildCondition(payment, buyerUserId, condition);

        JPAQuery<PaymentSummaryResponse> query = queryFactory
                .select(Projections.constructor(PaymentSummaryResponse.class,
                        payment.id,
                        payment.auctionId,
                        payment.productId,
                        payment.productName,
                        payment.productThumbnailUrl,
                        payment.sellerNickname,
                        payment.buyerNickname,
                        payment.amount,
                        payment.status,
                        payment.paymentDeadlineAt,
                        payment.paidAt,
                        payment.refundedAt
                ))
                .from(payment)
                .where(builder)
                .orderBy(payment.createdDate.desc(), payment.id.desc());

        List<PaymentSummaryResponse> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(payment.count())
                .from(payment)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder buildCondition(QPayment payment, Long buyerUserId, PaymentSearchCondition condition) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(payment.buyerUserId.eq(buyerUserId));

        if (condition == null) {
            return builder;
        }

        if (condition.status() != null) {
            builder.and(payment.status.eq(condition.status()));
        }

        if (condition.paymentType() == PaymentViewType.REQUIRED) {
            builder.and(payment.status.in(PaymentStatus.REQUESTED, PaymentStatus.PG_PENDING));
            builder.and(payment.paymentDeadlineAt.goe(LocalDateTime.now()));
        } else if (condition.paymentType() == PaymentViewType.HISTORY) {
            builder.and(payment.status.notIn(PaymentStatus.REQUESTED, PaymentStatus.PG_PENDING)
                    .or(payment.paymentDeadlineAt.before(LocalDateTime.now())));
        }

        return builder;
    }
}
