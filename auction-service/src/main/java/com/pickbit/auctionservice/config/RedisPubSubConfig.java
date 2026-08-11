package com.pickbit.auctionservice.config;

import com.pickbit.auctionservice.application.event.WebSocketRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            WebSocketRedisSubscriber subscriber,
            ThreadPoolTaskExecutor auctionPubSubExecutor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 메시지 전달용 실행기. 지정하지 않으면 SimpleAsyncTaskExecutor 가 쓰이는데,
        // 그것은 메시지마다 새 스레드를 만든다. 입찰이 몰리는 경매에서 WebSocket 팬아웃
        // 메시지가 쏟아지면 스레드가 무한히 생긴다.
        container.setTaskExecutor(auctionPubSubExecutor);

        // 구독 실행기는 반드시 따로 지정해야 한다.
        // 지정하지 않으면 taskExecutor 를 같이 쓰는데, 구독 태스크는 스레드를 영구히 점유하는
        // 장기 실행 작업이라 위의 유계 풀을 굶긴다. 그러면 컨테이너가 구독을 다시 맺으면서
        // 같은 메시지가 두 번 전달된다. (실제로 이 문제를 겪었고 WebSocketRedisSubscriberTest 가 잡아냈다)
        container.setSubscriptionExecutor(subscriptionExecutor());

        container.addMessageListener(subscriber, new PatternTopic("auction:ws:*"));
        return container;
    }

    /**
     * 구독 태스크 전용 실행기. 구독은 패턴 하나뿐이고 그 스레드는 계속 블로킹된 채 남아 있으므로
     * 개수 제한이 있는 풀을 쓰면 안 된다.
     */
    private SimpleAsyncTaskExecutor subscriptionExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("auction-ws-sub-");
        executor.setConcurrencyLimit(SimpleAsyncTaskExecutor.UNBOUNDED_CONCURRENCY);
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor auctionPubSubExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        // 큐를 두지 않는다. ThreadPoolExecutor 는 큐가 가득 차야 core 이상으로 스레드를 늘리므로,
        // 큐가 깊으면 실시간 이벤트가 스레드 4개 뒤에 줄을 서서 전송이 지연된다.
        // 경매 실시간 전송은 지연이 곧 체감 품질이라 대기보다 스레드 확장을 택한다.
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("auction-ws-");
        // 최대 스레드까지 찼으면 호출 스레드가 직접 처리해 역압이 걸리게 한다.
        // 실시간 이벤트를 버리는 것보다 잠깐 느려지는 편이 낫다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
