package com.pickbit.library.inbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실패한 Kafka 이벤트 재처리 설정입니다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "inbox.retry")
public class InboxRetryProperties {

    private boolean enabled = true;

    /** 재처리 주기. 인라인 재시도(약 1분)로 안 되면 여기서 더 긴 호흡으로 다시 시도한다. */
    private String cron = "0 */2 * * * *";

    /** 한 번에 처리할 건수. */
    private int batchSize = 100;

    /**
     * 최대 재처리 횟수. 이 횟수를 넘으면 더 시도하지 않고 사람이 봐야 하는 상태로 남는다.
     * 기본값이면 2분 주기 * 백오프로 대략 하루 정도를 커버한다.
     */
    private int maxAttempts = 10;

    /** 재처리 실패 시 다음 시도까지의 기본 대기 시간(초). 시도 횟수만큼 지수적으로 늘어난다. */
    private long baseBackoffSeconds = 60;

    /** 백오프 상한(초). */
    private long maxBackoffSeconds = 3_600;
}
