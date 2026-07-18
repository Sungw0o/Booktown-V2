package com.booktown.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "contentProcessingExecutor")
    public Executor contentProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("content-proc-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "summaryProcessingExecutor")
    public Executor summaryProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("summary-proc-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "quizProcessingExecutor")
    public Executor quizProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("quiz-proc-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "illustrationProcessingExecutor")
    public Executor illustrationProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("illus-proc-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new BlockingBackpressurePolicy());
        executor.initialize();
        return executor;
    }

    private static final class BlockingBackpressurePolicy implements RejectedExecutionHandler {

        private static final long OFFER_TIMEOUT_MILLIS = 250;

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            try {
                while (!executor.isShutdown()) {
                    if (executor.getQueue().offer(task, OFFER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting for illustration queue capacity.", e);
            }
            throw new RejectedExecutionException("Illustration executor is shutting down.");
        }
    }
}
