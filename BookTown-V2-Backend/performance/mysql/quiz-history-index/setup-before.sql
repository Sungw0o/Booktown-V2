DROP DATABASE IF EXISTS booktown_index_evidence;
CREATE DATABASE booktown_index_evidence;
USE booktown_index_evidence;

CREATE TABLE digit (
    value TINYINT NOT NULL PRIMARY KEY
);

INSERT INTO digit (value)
VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

CREATE TABLE quiz_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    quiz_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL,
    correct_count INT NOT NULL,
    total_count INT NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_quiz_submission_user_id (user_id),
    KEY idx_quiz_submission_quiz_id (quiz_id)
) ENGINE=InnoDB;

INSERT INTO quiz_submission (
    quiz_id,
    user_id,
    score,
    correct_count,
    total_count,
    submitted_at
)
SELECT
    MOD(sequence_number, 10000) + 1,
    CASE
        WHEN sequence_number < 100000 THEN 42
        ELSE MOD(sequence_number, 10000) + 1000
    END,
    MOD(sequence_number, 101),
    MOD(sequence_number, 11),
    10,
    TIMESTAMP('2025-01-01 00:00:00') + INTERVAL sequence_number SECOND
FROM (
    SELECT
        d0.value
        + d1.value * 10
        + d2.value * 100
        + d3.value * 1000
        + d4.value * 10000
        + d5.value * 100000 AS sequence_number
    FROM digit d0
    CROSS JOIN digit d1
    CROSS JOIN digit d2
    CROSS JOIN digit d3
    CROSS JOIN digit d4
    CROSS JOIN digit d5
) numbers
WHERE sequence_number < 500000;

ANALYZE TABLE quiz_submission;

SELECT COUNT(*) AS total_rows FROM quiz_submission;
SELECT COUNT(*) AS heavy_user_rows FROM quiz_submission WHERE user_id = 42;
