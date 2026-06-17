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
        Long paymentId = transactionTemplate.execute(status -> prepareConfirm(buyerUserId, req));

        TossPaymentResponse response;
        try {
            response = tossPaymentsClient.confirm(req.paymentKey(), req.orderId(), req.amount());
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> markPgFailedIfPending(paymentId));
            throw e;
        }

        return transactionTemplate.execute(status -> completeConfirm(paymentId, response));
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

        if (payment.getStatus() == PaymentStatus.RELEASED) {
            return PaymentDetailResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.ESCROWED) {
            throw new InvalidPaymentStatusException("결제 완료 상태에서만 구매확정할 수 있습니다. status=" + payment.getStatus());
        }

        releasePayment(payment, LocalDateTime.now());
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
        releasePayment(payment, now);
        return true;
    }

    private Long prepareConfirm(Long buyerUserId, PaymentConfirmRequest req) {
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(req.orderId())
                .orElseThrow(() -> new PaymentNotFoundException(req.orderId()));
        ensureBuyer(payment, buyerUserId);
        ensureAmount(payment, req.amount());
        ensureConfirmable(payment);

        if (payment.getStatus() == PaymentStatus.REQUESTED) {
            payment.markPgPending(req.paymentKey());
        }
        return payment.getId();
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

    private void releasePayment(Payment payment, LocalDateTime now) {
        payment.markReleased(now);
        Settlement settlement = settlementRepository.findByPaymentId(payment.getId())
                .orElseGet(() -> createSettlement(payment));
        settlement.markCompleted(now);
        outboxRecorder.paymentSettledEvent(payment, settlement);
    }

    private Settlement createSettlement(Payment payment) {
        Settlement settlement = Settlement.builder()
                .paymentId(payment.getId())
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
}
