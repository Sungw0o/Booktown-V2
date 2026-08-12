package com.booktown.domain.summary.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryTransactionBoundaryTest {

    @Test
    void processorRunsAsynchronouslyWithoutHoldingJpaTransaction() throws NoSuchMethodException {
        Method process = SummaryProcessor.class.getMethod("process", Long.class, boolean.class);

        assertThat(process.getAnnotation(Async.class)).isNotNull();
        assertThat(SummaryProcessor.class.getAnnotation(Transactional.class)).isNull();
        assertThat(process.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void jobStateChangesUseShortTransactions() throws NoSuchMethodException {
        assertTransactional("start", Long.class);
        assertTransactional("updateBookMetadata", Long.class, String.class, String.class, String.class);
        assertTransactional("complete", Long.class, String.class);
        assertTransactional("fail", Long.class, String.class, boolean.class);
    }

    private void assertTransactional(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = SummaryJobTransactionService.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }
}
