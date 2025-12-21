package com.hhplus.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 비동기 작업 설정
 *
 * 스레드 풀 구성:
 * - Core Pool Size: 10
 * - Max Pool Size: 50
 * - Queue Capacity: 100
 *
 * MDC 전파:
 * - 비동기 작업에서도 로그 컨텍스트 정보 유지
 * - TaskDecorator를 통해 MDC를 복사하여 전달
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 비동기 작업용 스레드 풀 설정
     *
     * 기본 SimpleAsyncTaskExecutor는 매번 새 스레드를 생성하므로
     * ThreadPoolTaskExecutor로 스레드 풀을 구성하여 성능 향상
     */
    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 스레드 풀 기본 크기
        executor.setCorePoolSize(10);

        // 스레드 풀 최대 크기
        executor.setMaxPoolSize(50);

        // 큐 용량 (큐가 가득 차면 max pool size까지 스레드 생성)
        executor.setQueueCapacity(100);

        // 스레드 이름 접두사
        executor.setThreadNamePrefix("async-");

        // MDC 전파를 위한 Decorator 설정
        executor.setTaskDecorator(new MdcTaskDecorator());

        // 거부 정책: CallerRunsPolicy (큐가 가득 차면 호출한 스레드에서 실행)
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // 종료 시 대기 중인 작업 완료
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 종료 대기 시간 (초)
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("ThreadPoolTaskExecutor 초기화 완료 - Core: {}, Max: {}, Queue: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * 비동기 예외 핸들러
     *
     * 비동기 작업 중 발생한 예외를 처리합니다.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("비동기 작업 예외 발생 - Method: {}, Params: {}",
                    method.getName(), params, throwable);
        };
    }

    /**
     * MDC 전파를 위한 TaskDecorator
     *
     * 비동기 작업 실행 전에 부모 스레드의 MDC를 복사하여
     * 자식 스레드에 전달합니다.
     */
    public static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // 부모 스레드의 MDC 복사
            Map<String, String> contextMap = MDC.getCopyOfContextMap();

            return () -> {
                try {
                    // 자식 스레드에 MDC 설정
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }

                    // 실제 작업 실행
                    runnable.run();
                } finally {
                    // 작업 완료 후 MDC 정리
                    MDC.clear();
                }
            };
        }
    }
}
