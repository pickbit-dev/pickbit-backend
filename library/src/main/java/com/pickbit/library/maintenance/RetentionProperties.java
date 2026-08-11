package com.pickbit.library.maintenance;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 오래된 행을 주기적으로 지우기 위한 설정입니다.
 *
 * <p>아웃박스/인박스/웹훅 로그처럼 append-only 로 쌓이기만 하는 테이블이 대상입니다.
 * 이들은 삭제 로직이 전혀 없어 영원히 증가했습니다. 아웃박스는 Debezium 이 binlog 에서
 * 읽어가므로 발행 여부를 표시조차 하지 않고, 인박스는 멱등성 확인용 원장이라 계속 남습니다.
 *
 * <p>서비스마다 소유한 테이블이 다르므로 각 서비스의 {@code application.yml} 에서 목록을 정의합니다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "maintenance.retention")
public class RetentionProperties {

    private boolean enabled = true;

    /** 새벽 시간대에 한 번 돈다. */
    private String cron = "0 30 4 * * *";

    /** 한 번의 DELETE 로 지울 행 수. 크게 잡으면 binlog 가 한꺼번에 불어난다. */
    private int chunkSize = 1_000;

    /** 한 주기에 실행할 최대 청크 수. 정리 작업이 서비스 시간을 잡아먹지 않게 상한을 둔다. */
    private int maxChunksPerRun = 200;

    private List<Target> targets = new ArrayList<>();

    /**
     * @param table 테이블 이름
     * @param days  보관 기간(일). 이보다 오래된 행을 지운다
     */
    @Getter
    @Setter
    public static class Target {
        private String table;
        private int days;
    }
}
