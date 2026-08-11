package com.pickbit.userservice.config;

import com.pickbit.userservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.userservice.exception.kafka.KafkaInvalidMessageException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import com.pickbit.library.inbox.ExhaustedRetryRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    /** 토픽 파티션 수와 맞춘다. 이보다 크면 남는 소비자가 놀고, 작으면 파티션이 논다. */
    private static final int PARTITION_COUNT = 3;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> jsonConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "pickbit-user-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> jsonKafkaListenerContainerFactory(
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jsonConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        // 토픽 파티션이 3개인데 기본 동시성은 1이라 파티션 2개가 놀고 있었다.
        factory.setConcurrency(PARTITION_COUNT);
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ExhaustedRetryRecoverer recoverer) {
        // 인프라 장애(DB 재시작 등)는 몇 초 만에 끝나지 않는다. 기존 FixedBackOff(3s, 3회)는
        // 약 9초 만에 포기하고 오프셋을 넘겨버려 이벤트가 조용히 사라졌다.
        // 여기서는 약 1분간 지수 백오프로 버티고, 그래도 안 되면 인박스 재처리 스케줄러에 넘긴다.
        // 파티션을 계속 붙잡지 않도록 인라인 재시도는 짧게 유지한다.
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(20_000L);
        backOff.setMaxElapsedTime(60_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // 재시도해도 결과가 달라지지 않는 예외들. 즉시 실패 처리하고 파티션을 비워준다.
        errorHandler.addNotRetryableExceptions(KafkaDuplicateEventException.class, KafkaInvalidMessageException.class);
        return errorHandler;
    }
}
