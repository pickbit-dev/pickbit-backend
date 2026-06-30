package com.pickbit.paymentservice.application.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementBatchScheduler {

    private final JobOperator jobOperator;
    private final Job settlementJob;

    @Scheduled(cron = "${payment.settlement-batch.cron:-}")
    @SchedulerLock(name = "settlementBatch", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void runSettlementJob() throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        jobOperator.start(settlementJob, parameters);
        log.info("정산 배치 실행 완료. parameters={}", parameters);
    }
}
