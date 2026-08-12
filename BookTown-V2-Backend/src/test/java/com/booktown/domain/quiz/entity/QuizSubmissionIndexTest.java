package com.booktown.domain.quiz.entity;

import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class QuizSubmissionIndexTest {

    @Test
    void userHistoryIndexSupportsLatestSubmissionOrdering() {
        Table table = QuizSubmission.class.getAnnotation(Table.class);

        assertThat(Arrays.stream(table.indexes()))
                .extracting(Index::name, Index::columnList)
                .contains(tuple(
                        "idx_quiz_submission_user_submitted_at",
                        "user_id, submitted_at DESC"
                ))
                .doesNotContain(tuple(
                        "idx_quiz_submission_user_id",
                        "user_id"
                ));
    }

    @Test
    void schemaUsesSameCompositeIndex() throws IOException {
        try (InputStream schema = getClass().getResourceAsStream("/db/schema.sql")) {
            assertThat(schema).isNotNull();
            String sql = new String(schema.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains(
                    "KEY idx_quiz_submission_user_submitted_at (user_id, submitted_at DESC)"
            );
            assertThat(sql).doesNotContain("KEY idx_quiz_submission_user_id (user_id)");
        }
    }
}
