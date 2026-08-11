package com.pickbit.library.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionSchedulerTest {

    /** 지울 행이 몇 개 남았는지 흉내 내는 테스트용 cleaner. */
    private static class FakeCleaner extends RetentionCleaner {
        private final AtomicInteger remaining;
        private final List<String> calls = new ArrayList<>();

        private FakeCleaner(int remaining) {
            this.remaining = new AtomicInteger(remaining);
        }

        @Override
        public int deleteChunk(String table, LocalDateTime threshold, int chunkSize) {
            calls.add(table);
            int deleted = Math.min(chunkSize, remaining.get());
            remaining.addAndGet(-deleted);
            return deleted;
        }
    }

    private static RetentionProperties properties(int chunkSize, int maxChunks, String... tables) {
        RetentionProperties properties = new RetentionProperties();
        properties.setChunkSize(chunkSize);
        properties.setMaxChunksPerRun(maxChunks);
        List<RetentionProperties.Target> targets = new ArrayList<>();
        for (String table : tables) {
            RetentionProperties.Target target = new RetentionProperties.Target();
            target.setTable(table);
            target.setDays(7);
            targets.add(target);
        }
        properties.setTargets(targets);
        return properties;
    }

    @Test
    @DisplayName("지울 행이 없으면 한 번만 조회하고 멈춘다")
    void stopsWhenNothingToDelete() {
        FakeCleaner cleaner = new FakeCleaner(0);

        new RetentionScheduler(properties(100, 50, "out_box_event"), cleaner).cleanUp();

        assertThat(cleaner.calls).hasSize(1);
    }

    @Test
    @DisplayName("청크가 덜 찰 때까지만 반복한다")
    void stopsOnPartialChunk() {
        // 250행, 청크 100 -> 100, 100, 50 으로 세 번이면 끝난다.
        FakeCleaner cleaner = new FakeCleaner(250);

        new RetentionScheduler(properties(100, 50, "out_box_event"), cleaner).cleanUp();

        assertThat(cleaner.calls).hasSize(3);
    }

    @Test
    @DisplayName("한 주기에 실행할 청크 수에 상한이 있다")
    void respectsMaxChunksPerRun() {
        // 지울 행이 아무리 많아도 정리 작업이 서비스 시간을 계속 잡아먹으면 안 된다.
        FakeCleaner cleaner = new FakeCleaner(1_000_000);

        new RetentionScheduler(properties(100, 5, "out_box_event"), cleaner).cleanUp();

        assertThat(cleaner.calls).hasSize(5);
    }

    @Test
    @DisplayName("테이블 하나가 실패해도 나머지는 계속 정리한다")
    void continuesAfterFailure() {
        FakeCleaner cleaner = new FakeCleaner(0) {
            @Override
            public int deleteChunk(String table, LocalDateTime threshold, int chunkSize) {
                if ("broken".equals(table)) {
                    throw new IllegalStateException("boom");
                }
                return super.deleteChunk(table, threshold, chunkSize);
            }
        };

        new RetentionScheduler(properties(100, 50, "broken", "inbox"), cleaner).cleanUp();

        assertThat(cleaner.calls).contains("inbox");
    }

    @Test
    @DisplayName("대상이 없으면 아무 것도 하지 않는다")
    void noTargetsIsNoOp() {
        FakeCleaner cleaner = new FakeCleaner(1000);

        new RetentionScheduler(properties(100, 50), cleaner).cleanUp();

        assertThat(cleaner.calls).isEmpty();
    }

    @Test
    @DisplayName("비활성이면 아무 것도 하지 않는다")
    void disabledIsNoOp() {
        FakeCleaner cleaner = new FakeCleaner(1000);
        RetentionProperties properties = properties(100, 50, "out_box_event");
        properties.setEnabled(false);

        new RetentionScheduler(properties, cleaner).cleanUp();

        assertThat(cleaner.calls).isEmpty();
    }
}
