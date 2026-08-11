package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.config.BidArbiterProperties;
import com.pickbit.auctionservice.infrastructure.redis.AuctionRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code auction:bid:stream} 을 소비해 입찰을 DB에 반영합니다.
 *
 * <p>소비자 그룹을 쓰므로 처리하지 못한 입찰은 XACK 되지 않고 pending 으로 남습니다.
 * 프로세스가 죽어도 재기동 후 이어서 처리할 수 있습니다.
 *
 * <p>단일 소비자로 돌립니다. 같은 경매의 입찰이 순번대로 도착해야 "직전 최고가를 OUTBID 로
 * 내리고 새 입찰을 ACTIVE 로 올리는" 처리가 어긋나지 않기 때문입니다. 배치로 묶어 커밋하므로
 * 단일 소비자여도 처리량은 충분합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auction.bid.arbiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BidPersistenceWorker implements SmartLifecycle {

    private static final String CONSUMER_NAME = "worker-1";

    private final StringRedisTemplate redis;
    private final BidArbiterProperties properties;
    private final BidBatchPersister persister;

    private volatile boolean running;
    private Thread worker;

    @Override
    public void start() {
        ensureConsumerGroup();
        running = true;
        worker = Thread.ofPlatform()
                .name("bid-persistence-worker")
                .daemon(true)
                .start(this::run);
        log.info("입찰 영속화 워커를 시작했습니다.");
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        log.info("입찰 영속화 워커를 중지했습니다.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void ensureConsumerGroup() {
        try {
            // MKSTREAM 이 필요하다. StreamOperations.createGroup(key, group) 은 MKSTREAM 없이
            // XGROUP CREATE 를 보내므로 스트림이 아직 없으면 실패하고,
            // 그 뒤 XREADGROUP 이 계속 NOGROUP 으로 죽는다.
            redis.execute((RedisCallback<Object>) connection -> connection.streamCommands().xGroupCreate(
                    AuctionRedisKeys.BID_STREAM.getBytes(StandardCharsets.UTF_8),
                    AuctionRedisKeys.CONSUMER_GROUP,
                    ReadOffset.latest(),
                    true));
            log.info("입찰 스트림 소비자 그룹을 생성했습니다.");
        } catch (RuntimeException e) {
            if (isAlreadyExists(e)) {
                log.debug("입찰 스트림 소비자 그룹이 이미 존재합니다.");
                return;
            }
            throw e;
        }
    }

    /** BUSYGROUP 이외의 오류는 삼키지 않는다. 삼키면 소비가 조용히 멈춘다. */
    private static boolean isAlreadyExists(RuntimeException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    private void run() {
        while (running) {
            try {
                // 먼저 이 소비자에게 배달됐지만 ACK 하지 못한 것부터 처리한다 (재기동 복구).
                if (!drain(ReadOffset.from("0"))) {
                    drain(ReadOffset.lastConsumed());
                }
            } catch (Exception e) {
                if (!running) {
                    return;
                }
                log.error("입찰 영속화 중 오류가 발생했습니다. 재시도합니다.", e);
                sleepQuietly();
            }
        }
    }

    /**
     * @return 처리한 항목이 있으면 {@code true}
     */
    // read(...) 의 StreamOffset 가변인자가 제네릭 배열이라 경고가 나온다. 인자를 하나만 넘기므로 안전하다.
    @SuppressWarnings("unchecked")
    private boolean drain(ReadOffset offset) {
        List<MapRecord<String, Object, Object>> messages = redis.opsForStream().read(
                Consumer.from(AuctionRedisKeys.CONSUMER_GROUP, CONSUMER_NAME),
                StreamReadOptions.empty()
                        .count(properties.getBatchSize())
                        .block(Duration.ofMillis(properties.getBlockMillis())),
                StreamOffset.create(AuctionRedisKeys.BID_STREAM, offset));

        if (messages == null || messages.isEmpty()) {
            return false;
        }

        List<BidRecord> records = new ArrayList<>(messages.size());
        List<RecordId> ids = new ArrayList<>(messages.size());
        for (MapRecord<String, Object, Object> message : messages) {
            try {
                records.add(BidRecord.from(toStringMap(message)));
                ids.add(message.getId());
            } catch (RuntimeException e) {
                // 해석할 수 없는 항목이 스트림을 영원히 막지 않게 ACK 하고 넘어간다.
                log.error("입찰 스트림 항목을 해석할 수 없어 건너뜁니다. id={} | fields={}",
                        message.getId(), message.getValue(), e);
                ack(message.getId());
            }
        }

        if (records.isEmpty()) {
            return true;
        }

        persister.persist(records);
        redis.opsForStream().acknowledge(
                AuctionRedisKeys.BID_STREAM, AuctionRedisKeys.CONSUMER_GROUP, ids.toArray(new RecordId[0]));
        log.debug("입찰 {}건을 반영했습니다.", records.size());
        return true;
    }

    private void ack(RecordId id) {
        redis.opsForStream().acknowledge(AuctionRedisKeys.BID_STREAM, AuctionRedisKeys.CONSUMER_GROUP, id);
    }

    private static Map<String, String> toStringMap(MapRecord<String, Object, Object> message) {
        Map<String, String> fields = new HashMap<>();
        message.getValue().forEach((k, v) -> fields.put(String.valueOf(k), String.valueOf(v)));
        return fields;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
