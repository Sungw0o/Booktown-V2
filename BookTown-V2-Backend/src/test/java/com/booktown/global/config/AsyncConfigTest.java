package com.booktown.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    void illustrationExecutorUsesCostBoundedParallelismAndBackpressure() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().illustrationProcessingExecutor();
        try {
            ThreadPoolExecutor nativeExecutor = executor.getThreadPoolExecutor();

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(nativeExecutor.getQueue().remainingCapacity()).isEqualTo(20);
            assertThat(nativeExecutor.getRejectedExecutionHandler().getClass().getSimpleName())
                    .isEqualTo("BlockingBackpressurePolicy");
        } finally {
            executor.shutdown();
        }
    }
}
