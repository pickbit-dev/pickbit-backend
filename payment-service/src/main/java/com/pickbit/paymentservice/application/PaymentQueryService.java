package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.api.dto.response.PaymentDetailResponse;
import com.pickbit.paymentservice.api.dto.response.PaymentRequestInfoResponse;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.exception.PaymentAccessDeniedException;
import com.pickbit.paymentservice.exception.PaymentNotFoundException;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final PgOrderIdAssigner pgOrderIdAssigner;

    @Value("${client.toss-payments.success-url}")
    private String successUrl;

    @Value("${client.toss-payments.fail-url}")
    private String failUrl;

    @Transactional
    public PaymentRequestInfoResponse getRequestInfo(Long buyerUserId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        ensureBuyer(payment, buyerUserId);

        String pgOrderId = pgOrderIdAssigner.assignIfNeeded(payment);

        return new PaymentRequestInfoResponse(
                payment.getId(),
                pgOrderId,
                payment.getAmount(),
                "경매 #" + payment.getAuctionId() + " 낙찰",
                "buyer-" + payment.getBuyerUserId(),
                successUrl,
                failUrl,
                payment.getPaymentDeadlineAt()
        );
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getPayment(Long buyerUserId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        ensureBuyer(payment, buyerUserId);
        return PaymentDetailResponse.from(payment);
    }

    private void ensureBuyer(Payment payment, Long buyerUserId) {
        if (!payment.getBuyerUserId().equals(buyerUserId)) {
            throw new PaymentAccessDeniedException();
        }
    }
}
