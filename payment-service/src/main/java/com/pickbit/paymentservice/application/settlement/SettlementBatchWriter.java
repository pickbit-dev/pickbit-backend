package com.pickbit.paymentservice.application.settlement;

import com.pickbit.paymentservice.application.OutboxRecorder;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.infrastructure.persistence.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SettlementBatchWriter implements ItemWriter<SettlementBatchItem> {

    private final OutboxRecorder outboxRecorder;
    private final SettlementRepository settlementRepository;

    @Override
    public void write(Chunk<? extends SettlementBatchItem> chunk) {
        LocalDateTime now = LocalDateTime.now();
        List<Settlement> changed = new ArrayList<>(chunk.size());

        for (SettlementBatchItem item : chunk) {
            if (item.success()) {
                item.payment().markReleased(now);
                item.settlement().markCompleted(now);
                outboxRecorder.paymentSettledEvent(item.payment(), item.settlement());
            } else {
                item.settlement().markFailed(item.failureReason());
            }
            changed.add(item.settlement());
        }

        // JpaPagingItemReader 는 자기만의 EntityManager 로 읽고 페이지마다 clear 하기 때문에
        // 여기 들어온 Settlement 는 스텝 트랜잭션에 붙어 있지 않다(detached). 명시적으로 merge 하지
        // 않으면 위의 markCompleted/markFailed 가 전부 조용히 버려진다.
        // Payment 는 프로세서가 리포지터리로 조회해 managed 상태라 더티 체킹으로 저장된다 —
        // 그래서 결제만 RELEASED 가 되고 정산은 영원히 PENDING 으로 남는 증상이 나왔다.
        settlementRepository.saveAll(changed);
    }
}
