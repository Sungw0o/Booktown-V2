USE booktown_index_evidence;

EXPLAIN ANALYZE
SELECT id, quiz_id, user_id, score, correct_count, total_count, submitted_at
FROM quiz_submission
WHERE user_id = 42
ORDER BY submitted_at DESC
LIMIT 20;
