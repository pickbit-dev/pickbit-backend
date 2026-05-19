package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PgOrderIdAssigner {

    public String assignIfNeeded(Payment payment) {
        if (payment.getPgOrderId() == null) {
            payment.assignPgOrderId(UUID.randomUUID().toString());
        }
        return payment.getPgOrderId();
    }
}
