package com.booktown.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncConfigTest {

    @Test
    void illustrationExecutorUsesCostBoundedParallelism() {
        ThreadPoolTaskExecutor executor = createIllustrationExecutor();
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

    @Test
    void saturatedIllustrationQueueBlocksThenAcceptsWorkAfterCapacityIsReleased() throws Exception {
        ThreadPoolTaskExecutor executor = createIllustrationExecutor();
        ExecutorService submitter = Executors.newSingleThreadExecutor();
        CountDownLatch workersStarted = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        CountDownLatch backpressuredTaskRan = new CountDownLatch(1);

        try {
            Runnable blockingTask = () -> {
                workersStarted.countDown();
                await(releaseWorkers);
            };
            executor.execute(blockingTask);
            executor.execute(blockingTask);
            assertThat(workersStarted.await(2, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i < 20; i++) {
                executor.execute(() -> { });
            }

            Future<?> blockedSubmission = submitter.submit(
                    () -> executor.execute(backpressuredTaskRan::countDown)
            );
            Thread.sleep(100);
            assertThat(blockedSubmission.isDone()).isFalse();

            releaseWorkers.countDown();
            blockedSubmission.get(2, TimeUnit.SECONDS);
            assertThat(backpressuredTaskRan.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseWorkers.countDown();
            submitter.shutdownNow();
            executor.shutdown();
        }
    }

    @Test
    void backpressureRejectsWhenExecutorShutsDown() {
        ThreadPoolTaskExecutor executor = createIllustrationExecutor();
        ThreadPoolExecutor nativeExecutor = executor.getThreadPoolExecutor();
        RejectedExecutionHandler handler = nativeExecutor.getRejectedExecutionHandler();
        executor.shutdown();

        assertThatThrownBy(() -> handler.rejectedExecution(() -> { }, nativeExecutor))
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessageContaining("shutting down");
    }

    @Test
    void backpressureRestoresInterruptAndRejectsWaitingSubmission() throws Exception {
        ThreadPoolTaskExecutor configuredExecutor = createIllustrationExecutor();
        RejectedExecutionHandler handler = configuredExecutor.getThreadPoolExecutor().getRejectedExecutionHandler();
        CountDownLatch releaseWorker = new CountDownLatch(1);
        ThreadPoolExecutor saturatedExecutor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1)
        );
        saturatedExecutor.execute(() -> await(releaseWorker));
        saturatedExecutor.getQueue().put(() -> { });

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread waitingSubmitter = new Thread(() -> {
            try {
                handler.rejectedExecution(() -> { }, saturatedExecutor);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        try {
            waitingSubmitter.start();
            Thread.sleep(100);
            waitingSubmitter.interrupt();
            waitingSubmitter.join(2_000);

            assertThat(waitingSubmitter.isAlive()).isFalse();
            assertThat(waitingSubmitter.isInterrupted()).isTrue();
            assertThat(failure.get())
                    .isInstanceOf(RejectedExecutionException.class)
                    .hasMessageContaining("Interrupted");
        } finally {
            releaseWorker.countDown();
            saturatedExecutor.shutdownNow();
            configuredExecutor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor createIllustrationExecutor() {
        return (ThreadPoolTaskExecutor) new AsyncConfig().illustrationProcessingExecutor();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
