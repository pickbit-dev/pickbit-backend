package com.pickbit.library.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 테이블 이름은 바인딩 파라미터로 지정할 수 없어 SQL 에 문자열로 끼워 넣는다.
 * 설정 파일에서만 오는 값이지만, 실수나 오염된 설정이 그대로 SQL 이 되지 않도록 막는다.
 */
class RetentionCleanerTest {

    private final RetentionCleaner cleaner = new RetentionCleaner();

    @ParameterizedTest
    @DisplayName("식별자로 쓸 수 없는 이름은 거부한다")
    @ValueSource(strings = {
            "out_box_event; DROP TABLE payment",
            "out_box_event WHERE 1=1 OR",
            "out box event",
            "out-box-event",
            "1_starts_with_digit",
            "",
            "'",
    })
    void rejectsUnsafeTableNames(String table) {
        assertThatThrownBy(() -> cleaner.deleteChunk(table, LocalDateTime.now(), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 테이블 이름");
    }

    @ParameterizedTest
    @DisplayName("정상적인 테이블 이름은 이름 검증을 통과한다")
    @ValueSource(strings = {"out_box_event", "inbox", "pg_webhook_log", "auction_event", "_leading_underscore"})
    void acceptsValidTableNames(String table) {
        // EntityManager 가 주입되지 않은 단위 테스트라 이름 검증을 통과하면 NPE 로 진행된다.
        // IllegalArgumentException 이 아니라는 것이 곧 이름 검증 통과를 뜻한다.
        assertThatThrownBy(() -> cleaner.deleteChunk(table, LocalDateTime.now(), 100))
                .isNotInstanceOf(IllegalArgumentException.class);
    }
}
