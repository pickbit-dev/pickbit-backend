package com.pickbit.library.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 설정된 테이블에서 보관 기간이 지난 행을 지웁니다.
 *
 * <p>여러 인스턴스가 동시에 돌아도 같은 행을 두 번 지우려 할 뿐 결과는 같으므로 분산 락을
 * 두지 않았습니다. 다만 인스턴스를 늘릴 계획이라면 ShedLock 을 붙이는 편이 낫습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final RetentionProperties properties;
    private final RetentionCleaner cleaner;

    @Scheduled(cron = "${maintenance.retention.cron:0 30 4 * * *}")
    public void cleanUp() {
        if (!properties.isEnabled() || properties.getTargets().isEmpty()) {
            return;
        }

        for (RetentionProperties.Target target : properties.getTargets()) {
            try {
                cleanTable(target);
            } catch (RuntimeException e) {
                // 한 테이블이 실패해도 나머지는 계속 정리한다.
                log.error("보관 기간 정리 실패 | table={}", target.getTable(), e);
            }
        }
    }

    private void cleanTable(RetentionProperties.Target target) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(target.getDays());
        long totalDeleted = 0;

        for (int chunk = 0; chunk < properties.getMaxChunksPerRun(); chunk++) {
            int deleted = cleaner.deleteChunk(target.getTable(), threshold, properties.getChunkSize());
            totalDeleted += deleted;
            if (deleted < properties.getChunkSize()) {
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info("보관 기간 정리 | table={} | days={} | deleted={}",
                    target.getTable(), target.getDays(), totalDeleted);
        }
    }
}
