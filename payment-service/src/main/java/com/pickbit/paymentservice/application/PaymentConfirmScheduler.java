package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfirmScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentCommandService paymentCommandService;

    @Scheduled(cron = "${payment.confirm-scheduler.cron:-}")
    @SchedulerLock(name = "autoConfirmPurchases", lockAtMostFor = "PT30S", lockAtLeastFor = "PT5S")
    public void autoConfirmPurchases() {
        LocalDateTime now = LocalDateTime.now();
        List<Payment> targets = paymentRepository.findByStatusAndConfirmDeadlineAtBefore(PaymentStatus.ESCROWED, now);
        if (targets.isEmpty()) return;

        int confirmed = 0;
        for (Payment payment : targets) {
            if (paymentCommandService.autoConfirmPurchase(payment.getId(), now)) {
                confirmed++;
            }
        }
        log.info("자동 구매확정 처리: {}건", confirmed);
    }
}
