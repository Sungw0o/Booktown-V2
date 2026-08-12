-- ============================================================================
-- V202608120001__perf_indexes.sql
--
-- 실행계획(EXPLAIN ANALYZE) 실측에 근거한 인덱스 변경
--
-- 대상        : 이미 데이터가 적재되어 있는 기존 DB (schema.sql 로 새로 만드는 DB 는 불필요)
-- 측정 근거   : perf/results/explain/BEFORE.txt, AFTER.txt, BENCH_*.txt
-- 측정 환경   : MySQL 8.0.46, innodb_buffer_pool_size=512M
-- 측정 데이터 : book 200,000 / quiz_submission 2,000,000 / summary_job 1,000,000 /
--               illustration_job 500,000 / users 20,000
--
-- ⚠️ 운영 적용 시 주의
--   - InnoDB 는 온라인 DDL 을 지원하지만 대용량 테이블에서는 시간이 걸린다.
--     (측정 환경 200만 행 기준 CREATE INDEX 약 10초 소요)
--   - 트래픽이 적은 시간대에 적용하고, 적용 후 ANALYZE TABLE 로 통계를 갱신할 것.
--   - 롤백 SQL 은 파일 하단에 주석으로 제공한다.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. quiz_submission : 퀴즈 이력 조회의 filesort 제거
--
--    대상 쿼리 (QuizSubmissionRepository.findAllByUserIdOrderBySubmittedAtDesc)
--      SELECT ... FROM quiz_submission WHERE user_id = ? ORDER BY submitted_at DESC LIMIT ?
--
--    Before : type=ref, key=idx_quiz_submission_user_id, Extra=Using filesort
--             → 해당 사용자의 전체 행(측정 시 10,050행)을 읽어 정렬한 뒤 20행만 사용
--    After  : type=ref, key=idx_quiz_submission_user_submitted, Extra=Backward index scan
--             → 인덱스를 역방향으로 20행만 읽고 종료 (정렬 연산 자체가 사라짐)
--
--    컬럼 순서 근거 : user_id 는 등치(Equality), submitted_at 은 정렬(Sort) 조건이다.
--                    Equality → Sort 순서여야 인덱스 하나로 필터와 정렬을 동시에 해결한다.
--                    (반대 순서 (submitted_at, user_id) 실측 시 오히려 2배 느렸다)
--
--    ASC/DESC 근거 : MySQL 8.0 의 Backward index scan 으로 DESC 정렬이 해결되므로
--                    명시적 DESC 인덱스는 불필요했다. 실측상 유의미한 차이가 없었다.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_quiz_submission_user_submitted
    ON quiz_submission (user_id, submitted_at);

-- 위 복합 인덱스의 좌측 프리픽스가 (user_id) 이므로 기존 단일 인덱스는 완전히 중복이다.
-- 삭제 전 quiz_submission 을 참조하는 모든 쿼리의 실행계획을 확인했고 회귀는 없었다.
--   - existsByQuizIdAndUserId : idx_quiz_submission_quiz_id 사용 (영향 없음)
--   - Page count 쿼리         : 복합 인덱스로도 covering index(Using index) 유지
DROP INDEX idx_quiz_submission_user_id ON quiz_submission;


-- ---------------------------------------------------------------------------
-- 2. summary_job : 일일 생성 횟수 제한 조회를 covering index 로 전환
--
--    대상 쿼리 (SummaryJobRepository.countByUserIdAndCreatedAtAfter)
--      SELECT COUNT(*) FROM summary_job WHERE user_id = ? AND created_at > ?
--
--    Before : type=ref, key=idx_summary_job_user_book(user_id, book_id), filtered=33.33
--             → user_id 로만 인덱스를 타고, created_at 은 인덱스에 없어
--               해당 사용자의 전체 행(측정 시 5,025행)을 테이블에서 읽어 필터링
--    After  : type=range, key=idx_summary_job_user_created, Extra=Using where; Using index
--             → Covering index range scan 으로 조건에 맞는 16행만 인덱스에서 읽고 종료
--               (테이블 접근 0회)
--
--    컬럼 순서 근거 : user_id 는 등치(Equality), created_at 은 범위(Range) 조건이다.
--                    Equality → Range 순서여야 범위 조건이 인덱스 스캔 구간을 좁힌다.
--                    (반대 순서 (created_at, user_id) 실측 시 2,866행을 스캔해 18배 느렸다)
--
--    기존 idx_summary_job_user_book 은 삭제하지 않는다.
--      existsByUserIdAndBookIdAndStatusIn(중복 Job 방지)이 (user_id, book_id) 를 그대로 쓰고 있고,
--      실측상 이 쿼리는 인덱스 추가 후에도 동일 인덱스를 계속 사용해 회귀가 없었다.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_summary_job_user_created
    ON summary_job (user_id, created_at);


-- ---------------------------------------------------------------------------
-- 3. illustration_job : summary_job 과 동일한 패턴
--
--    대상 쿼리 (IllustrationJobRepository.countByUserIdAndCreatedAtAfter)
--      SELECT COUNT(*) FROM illustration_job WHERE user_id = ? AND created_at > ?
--
--    Before : type=ref, key=idx_illustration_job_user_id, filtered=33.33
--             → 2,513행을 테이블에서 읽어 9행으로 필터링
--    After  : type=range, Extra=Using where; Using index → 9행만 인덱스에서 읽음
--
--    ⚠️ quiz_submission 과 달리 여기서는 기존 단일 인덱스를 삭제하지 않는다.
--       existsByUserIdAndSceneIdAndStatusIn 의 실행계획이
--       index_merge intersect(idx_illustration_job_scene_id, idx_illustration_job_user_id)
--       이어서 user_id 단일 인덱스를 실제로 사용하고 있음을 실측으로 확인했다.
--       "복합 인덱스가 있으면 단일 인덱스는 항상 지워도 된다"가 아니라
--       테이블마다 실행계획을 확인해야 한다는 사례다.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_illustration_job_user_created
    ON illustration_job (user_id, created_at);


-- ---------------------------------------------------------------------------
-- 4. 통계 갱신 — 옵티마이저가 새 인덱스를 올바르게 선택하도록 반드시 실행
-- ---------------------------------------------------------------------------
ANALYZE TABLE quiz_submission, summary_job, illustration_job;


-- ---------------------------------------------------------------------------
-- 적용하지 않은 것 : book 검색(title/author LIKE '%q%') 의 FULLTEXT 전환
--
--   실측 결과 현행 LIKE 검색은 항상 full table scan 이며 비용이 데이터량에 정비례했다.
--     book   5,000건 →  2.17 ms
--     book  50,000건 → 16.48 ms
--     book 200,000건 → 64.42 ms   (목록 쿼리 기준, count 쿼리는 별도로 비슷한 비용)
--
--   ngram FULLTEXT 를 실제로 만들어 비교했으나 아래 이유로 이번 변경에는 포함하지 않았다.
--     (1) 효과가 검색어 선택도에 따라 정반대였다
--           매치 1건    : LIKE 75.35ms  vs  ngram 10.06ms  (ngram 7.5배 빠름)
--           매치 14,291건: LIKE 74.45ms  vs  ngram 158.32ms (ngram 2.1배 느림)
--     (2) 검색 결과 정합성이 달라졌다
--           'Gard' 검색 : LIKE 14,291건  vs  ngram 54,938건 (false positive)
--           'a'    검색 : LIKE 192,740건 vs  ngram 0건 (ngram_token_size=2 미만 검색 불가)
--     (3) 현재 운영 도서 수(약 5,000건) 기준 검색 비용은 2ms 대로 병목이 아니다.
--
--   → 도서 수가 5만 건을 넘어가는 시점에 재검토한다.
--     상세 근거는 perf/results/explain/BENCH_search*.txt 참조.
-- ---------------------------------------------------------------------------


-- ============================================================================
-- 롤백 SQL
-- ============================================================================
-- DROP INDEX idx_illustration_job_user_created ON illustration_job;
-- DROP INDEX idx_summary_job_user_created ON summary_job;
-- CREATE INDEX idx_quiz_submission_user_id ON quiz_submission (user_id);
-- DROP INDEX idx_quiz_submission_user_submitted ON quiz_submission;
-- ANALYZE TABLE quiz_submission, summary_job, illustration_job;
