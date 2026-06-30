package com.pickbit.paymentservice.application.settlement;

import com.pickbit.paymentservice.application.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SettlementBatchWriter implements ItemWriter<SettlementBatchItem> {

    private final OutboxRecorder outboxRecorder;

    @Override
    public void write(Chunk<? extends SettlementBatchItem> chunk) {
        LocalDateTime now = LocalDateTime.now();
        for (SettlementBatchItem item : chunk) {
            if (item.success()) {
                item.payment().markReleased(now);
                item.settlement().markCompleted(now);
                outboxRecorder.paymentSettledEvent(item.payment(), item.settlement());
                continue;
            }
            item.settlement().markFailed(item.failureReason());
        }
    }
}
