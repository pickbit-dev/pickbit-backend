package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.api.dto.request.PaymentConfirmRequest;
import com.pickbit.paymentservice.api.dto.response.PaymentDetailResponse;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import com.pickbit.paymentservice.exception.InvalidPaymentStatusException;
import com.pickbit.paymentservice.exception.PaymentAccessDeniedException;
import com.pickbit.paymentservice.exception.PaymentAmountMismatchException;
import com.pickbit.paymentservice.exception.PaymentNotFoundException;
import com.pickbit.paymentservice.infrastructure.client.TossPaymentsClient;
import com.pickbit.paymentservice.infrastructure.client.dto.TossPaymentResponse;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import com.pickbit.paymentservice.infrastructure.persistence.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final OutboxRecorder outboxRecorder;
    private final TransactionTemplate transactionTemplate;
    private final SettlementRepository settlementRepository;

    @Value("${payment.confirm-timeout-days:7}")
    private int confirmTimeoutDays;

    public PaymentDetailResponse confirm(Long buyerUserId, PaymentConfirmRequest req) {
        ConfirmPreparation preparation = transactionTemplate.execute(status -> prepareConfirm(buyerUserId, req));
        if (!preparation.requiresPgConfirm()) {
            return preparation.response();
        }

        TossPaymentResponse response;
        try {
            response = tossPaymentsClient.confirm(req.paymentKey(), req.orderId(), req.amount());
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> markPgFailedIfPending(preparation.paymentId()));
            throw e;
        }

        return transactionTemplate.execute(status -> completeConfirm(preparation.paymentId(), response));
    }

    @Transactional
    public PaymentDetailResponse refund(Long requesterUserId, Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        ensureBuyer(payment, requesterUserId);
        ensureRefundable(payment);

        tossPaymentsClient.cancel(payment.getPgPaymentKey(), reason, payment.getAmount());

        payment.markRefunded(LocalDateTime.now());
        outboxRecorder.paymentRefundedEvent(payment, reason);

        return PaymentDetailResponse.from(payment);
    }

    @Transactional
    public PaymentDetailResponse cancelBeforePayment(Long buyerUserId, Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        ensureBuyer(payment, buyerUserId);

        try {
            payment.markCancelledBeforePayment();
        } catch (IllegalStateException e) {
            throw new InvalidPaymentStatusException("결제 요청 상태에서만 결제 전 포기가 가능합니다. status=" + payment.getStatus());
        }
        outboxRecorder.paymentCancelledBeforePaymentEvent(payment);

        return PaymentDetailResponse.from(payment);
    }

    @Transactional
    public PaymentDetailResponse confirmPurchase(Long buyerUserId, Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        ensureBuyer(payment, buyerUserId);

        if (payment.getStatus() == PaymentStatus.PURCHASE_CONFIRMED || payment.getStatus() == PaymentStatus.RELEASED) {
            return PaymentDetailResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.ESCROWED) {
            throw new InvalidPaymentStatusException("결제 완료 상태에서만 구매확정할 수 있습니다. status=" + payment.getStatus());
        }

        confirmPurchaseAndCreatePendingSettlement(payment, LocalDateTime.now());
        return PaymentDetailResponse.from(payment);
    }

    @Transactional
    public boolean autoConfirmPurchase(Long paymentId, LocalDateTime now) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getStatus() != PaymentStatus.ESCROWED) {
            return false;
        }
        if (payment.getConfirmDeadlineAt() == null || payment.getConfirmDeadlineAt().isAfter(now)) {
            return false;
        }
        confirmPurchaseAndCreatePendingSettlement(payment, now);
        return true;
    }

    private ConfirmPreparation prepareConfirm(Long buyerUserId, PaymentConfirmRequest req) {
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(req.orderId())
                .orElseThrow(() -> new PaymentNotFoundException(req.orderId()));
        ensureBuyer(payment, buyerUserId);
        ensureAmount(payment, req.amount());

        if (payment.getStatus() == PaymentStatus.ESCROWED) {
            return ConfirmPreparation.alreadyConfirmed(PaymentDetailResponse.from(payment));
        }
        if (payment.getStatus() == PaymentStatus.PG_PENDING) {
            throw new InvalidPaymentStatusException("결제 승인 처리 중입니다. 잠시 후 결제 상태를 다시 확인해주세요. status=" + payment.getStatus());
        }
        ensureConfirmable(payment);

        payment.markPgPending(req.paymentKey());
        return ConfirmPreparation.requiresPgConfirm(payment.getId());
    }

    private PaymentDetailResponse completeConfirm(Long paymentId, TossPaymentResponse response) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getStatus() == PaymentStatus.ESCROWED) {
            return PaymentDetailResponse.from(payment);
        }
        payment.markEscrowed(response.paymentKey(), LocalDateTime.now(), confirmTimeoutDays);
        outboxRecorder.paymentEscrowedEvent(payment);
        return PaymentDetailResponse.from(payment);
    }

    private void markPgFailedIfPending(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getStatus() == PaymentStatus.PG_PENDING) {
            payment.markPgFailed();
        }
    }

    private void confirmPurchaseAndCreatePendingSettlement(Payment payment, LocalDateTime now) {
        payment.markPurchaseConfirmed(now);
        settlementRepository.findByPaymentId(payment.getId())
                .orElseGet(() -> createPendingSettlement(payment));
    }

    private Settlement createPendingSettlement(Payment payment) {
        Settlement settlement = Settlement.builder()
                .paymentId(payment.getId())
                // 판매자와 상품 정보를 스냅샷으로 함께 남긴다.
                // 정산 목록을 보여줄 때마다 Payment 와 조인하지 않아도 된다.
                .sellerUserId(payment.getSellerUserId())
                .auctionId(payment.getAuctionId())
                .productId(payment.getProductId())
                .productName(payment.getProductName())
                .productThumbnailUrl(payment.getProductThumbnailUrl())
                .grossAmount(payment.getAmount())
                .platformFeeAmount(BigDecimal.ZERO)
                .pgFeeAmount(BigDecimal.ZERO)
                .netSellerAmount(payment.getAmount())
                .status(SettlementStatus.PENDING)
                .build();
        return settlementRepository.save(settlement);
    }

    private void ensureBuyer(Payment payment, Long userId) {
        if (!payment.getBuyerUserId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }
    }

    private void ensureAmount(Payment payment, BigDecimal amount) {
        if (payment.getAmount().compareTo(amount) != 0) {
            throw new PaymentAmountMismatchException(payment.getAmount(), amount);
        }
    }

    private void ensureConfirmable(Payment payment) {
        if (payment.getStatus() != PaymentStatus.REQUESTED && payment.getStatus() != PaymentStatus.PG_PENDING) {
            throw new InvalidPaymentStatusException(
                    "이미 처리된 결제입니다. status=" + payment.getStatus());
        }
        if (payment.getPaymentDeadlineAt().isBefore(LocalDateTime.now())) {
            throw new InvalidPaymentStatusException("결제 시한이 지났습니다.");
        }
    }

    private void ensureRefundable(Payment payment) {
        if (payment.getStatus() != PaymentStatus.ESCROWED) {
            throw new InvalidPaymentStatusException(
                    "환불 가능한 상태가 아닙니다. status=" + payment.getStatus());
        }
    }

    private record ConfirmPreparation(
            Long paymentId,
            boolean requiresPgConfirm,
            PaymentDetailResponse response
    ) {
        static ConfirmPreparation requiresPgConfirm(Long paymentId) {
            return new ConfirmPreparation(paymentId, true, null);
        }

        static ConfirmPreparation alreadyConfirmed(PaymentDetailResponse response) {
            return new ConfirmPreparation(response.paymentId(), false, response);
        }
    }
}
