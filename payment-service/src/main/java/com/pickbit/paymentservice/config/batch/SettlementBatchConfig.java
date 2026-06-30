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
                .queryString("select s from Settlement s where s.status = :status order by s.id asc")
                .parameterValues(Map.of("status", SettlementStatus.PENDING))
                .pageSize(properties.chunkSize())
                .build();
    }
}
