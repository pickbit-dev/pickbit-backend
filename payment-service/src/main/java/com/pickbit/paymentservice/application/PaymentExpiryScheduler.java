package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentRepository paymentRepository;
    private final OutboxRecorder outboxRecorder;

    @Scheduled(cron = "${payment.expiry-scheduler.cron:-}")
    @SchedulerLock(name = "expirePayments", lockAtMostFor = "PT30S", lockAtLeastFor = "PT5S")
    @Transactional
    public void expirePayments() {
        LocalDateTime now = LocalDateTime.now();
        expireByStatus(PaymentStatus.REQUESTED, now);
    }

    private void expireByStatus(PaymentStatus status, LocalDateTime now) {
        List<Payment> expired = paymentRepository.findByStatusAndPaymentDeadlineAtBefore(status, now);
        if (expired.isEmpty()) return;

        for (Payment payment : expired) {
            paymentRepository.findByIdForUpdate(payment.getId())
                    .filter(locked -> locked.getStatus() == status)
                    .filter(locked -> locked.getPaymentDeadlineAt().isBefore(now))
                    .ifPresent(locked -> {
                        locked.markFailedNoPayment();
                        outboxRecorder.paymentFailedNoPaymentEvent(locked);
                    });
        }
        log.info("결제 만료 처리: status={}, count={}", status, expired.size());
    }
}
