package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.api.dto.request.PaymentConfirmRequest;
import com.pickbit.paymentservice.api.dto.response.PaymentDetailResponse;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.exception.InvalidPaymentStatusException;
import com.pickbit.paymentservice.exception.PaymentAccessDeniedException;
import com.pickbit.paymentservice.exception.PaymentAmountMismatchException;
import com.pickbit.paymentservice.exception.PaymentNotFoundException;
import com.pickbit.paymentservice.infrastructure.client.TossPaymentsClient;
import com.pickbit.paymentservice.infrastructure.client.dto.TossPaymentResponse;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final OutboxRecorder outboxRecorder;

    @Value("${payment.confirm-timeout-days:10}")
    private int confirmTimeoutDays;

    @Transactional
    public PaymentDetailResponse confirm(Long buyerUserId, PaymentConfirmRequest req) {
        Payment payment = paymentRepository.findByPgOrderId(req.orderId())
                .orElseThrow(() -> new PaymentNotFoundException(req.orderId()));
        ensureBuyer(payment, buyerUserId);
        ensureAmount(payment, req.amount());
        ensureConfirmable(payment);

        if (payment.getStatus() == PaymentStatus.REQUESTED) {
            payment.markPgPending(req.paymentKey());
        }

        TossPaymentResponse response = tossPaymentsClient.confirm(
                req.paymentKey(), req.orderId(), req.amount());

        payment.markEscrowed(response.paymentKey(), LocalDateTime.now(), confirmTimeoutDays);
        outboxRecorder.paymentEscrowedEvent(payment);

        return PaymentDetailResponse.from(payment);
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
