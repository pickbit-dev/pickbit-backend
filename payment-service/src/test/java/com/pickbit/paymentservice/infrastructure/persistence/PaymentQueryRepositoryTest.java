package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.api.dto.request.PaymentSearchCondition;
import com.pickbit.paymentservice.api.dto.request.PaymentViewType;
import com.pickbit.paymentservice.api.dto.response.PaymentSummaryResponse;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.config.TestContainerConfig;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestContainerConfig.class)
@ActiveProfiles("test")
@Transactional
class PaymentQueryRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentQueryRepository paymentQueryRepository;

    @Test
    @DisplayName("REQUIRED 조회는 로그인 사용자의 유효한 결제 대기 건만 반환한다")
    void searchMyPayments_required() {
        Payment required = savePayment(100L, PaymentStatus.REQUESTED, LocalDateTime.now().plusHours(1), "아이폰");
        savePayment(100L, PaymentStatus.PG_PENDING, LocalDateTime.now().minusMinutes(1), "만료된 결제");
        savePayment(100L, PaymentStatus.ESCROWED, LocalDateTime.now().plusHours(1), "결제 완료");
        savePayment(999L, PaymentStatus.REQUESTED, LocalDateTime.now().plusHours(1), "다른 사용자 결제");

        Page<PaymentSummaryResponse> page = paymentQueryRepository.searchMyPayments(
                100L,
                new PaymentSearchCondition(PaymentViewType.REQUIRED, null),
                PageRequest.of(0, 20)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().paymentId()).isEqualTo(required.getId());
        assertThat(page.getContent().getFirst().productName()).isEqualTo("아이폰");
    }

    @Test
    @DisplayName("status 조건이 있으면 해당 상태의 내 결제만 반환한다")
    void searchMyPayments_status() {
        savePayment(100L, PaymentStatus.REQUESTED, LocalDateTime.now().plusHours(1), "결제 대기");
        Payment escrowed = savePayment(100L, PaymentStatus.ESCROWED, LocalDateTime.now().plusHours(1), "결제 완료");
        savePayment(999L, PaymentStatus.ESCROWED, LocalDateTime.now().plusHours(1), "다른 사용자 결제");

        Page<PaymentSummaryResponse> page = paymentQueryRepository.searchMyPayments(
                100L,
                new PaymentSearchCondition(null, PaymentStatus.ESCROWED),
                PageRequest.of(0, 20)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().paymentId()).isEqualTo(escrowed.getId());
    }

    private Payment savePayment(Long buyerUserId, PaymentStatus status, LocalDateTime deadline, String productName) {
        return paymentRepository.save(Payment.builder()
                .auctionId(1L)
                .productId(10L)
                .productName(productName)
                .productThumbnailUrl("https://example.com/thumb.jpg")
                .buyerUserId(buyerUserId)
                .buyerNickname("buyer" + buyerUserId)
                .sellerUserId(200L)
                .sellerNickname("seller")
                .amount(BigDecimal.valueOf(50_000))
                .pgProvider(PgProvider.TOSS_PAYMENTS)
                .status(status)
                .paymentDeadlineAt(deadline)
                .build());
    }
}
