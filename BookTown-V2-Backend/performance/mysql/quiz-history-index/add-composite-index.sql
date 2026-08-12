USE booktown_index_evidence;

ALTER TABLE quiz_submission
    DROP INDEX idx_quiz_submission_user_id,
    ADD INDEX idx_quiz_submission_user_submitted_at (user_id, submitted_at DESC);

ANALYZE TABLE quiz_submission;
