package com.pickbit.library.maintenance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 보관 기간이 지난 행을 청크 단위로 삭제합니다.
 */
@Slf4j

public class RetentionCleaner {

    /**
     * 테이블 이름은 SQL 에 문자열로 끼워 넣어야 하므로(바인딩 파라미터로 지정할 수 없다)
     * 식별자로 쓸 수 있는 문자만 허용한다. 값은 설정 파일에서 오지만 방어해 둔다.
     */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 한 청크를 삭제합니다.
     *
     * <p>청크마다 트랜잭션을 분리합니다. 수십만 행을 한 트랜잭션에서 지우면 락이 오래 잡히고
     * binlog 가 한꺼번에 불어나 Debezium 과 디스크에 부담을 줍니다.
     *
     * @return 실제로 지운 행 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteChunk(String table, LocalDateTime threshold, int chunkSize) {
        if (!SAFE_TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("허용되지 않는 테이블 이름입니다: " + table);
        }
        return entityManager
                .createNativeQuery("DELETE FROM " + table + " WHERE created_date < :threshold LIMIT :chunkSize")
                .setParameter("threshold", threshold)
                .setParameter("chunkSize", chunkSize)
                .executeUpdate();
    }
}
