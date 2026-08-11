package com.pickbit.paymentservice.config.batch;

import com.pickbit.paymentservice.application.settlement.SettlementBatchItem;
import com.pickbit.paymentservice.application.settlement.SettlementBatchProcessor;
import com.pickbit.paymentservice.application.settlement.SettlementBatchWriter;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SettlementBatchProperties.class)
public class SettlementBatchConfig {

    private final SettlementBatchProperties properties;

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder("settlementJob", jobRepository)
                .start(settlementStep)
                .build();
    }

    @Bean
    public Step settlementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<Settlement> settlementReader,
            SettlementBatchProcessor processor,
            SettlementBatchWriter writer
    ) {
        return new StepBuilder("settlementStep", jobRepository)
                .<Settlement, SettlementBatchItem>chunk(properties.chunkSize())
                .reader(settlementReader)
                .processor(processor)
                .writer(writer)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<Settlement> settlementReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<Settlement>()
                .name("settlementReader")
                .entityManagerFactory(entityManagerFactory)
                // FAILED 도 대상에 포함한다. 예전에는 PENDING 만 조회해서 한 번 실패한 정산은
                // 영원히 재시도되지 않고 방치됐다 (상태 설명에는 "재시도 필요"라고 적혀 있었는데도).
                // 계속 실패하는 건이 매 주기 배치를 잡아먹지 않도록 재시도 횟수에 상한을 둔다.
                .queryString("""
                        select s from Settlement s
                        where s.status = :pending
                           or (s.status = :failed and s.retryCount < :maxRetries)
                        order by s.id asc
                        """)
                .parameterValues(Map.of(
                        "pending", SettlementStatus.PENDING,
                        "failed", SettlementStatus.FAILED,
                        "maxRetries", properties.maxRetries()))
                .pageSize(properties.chunkSize())
                .build();
    }
}
