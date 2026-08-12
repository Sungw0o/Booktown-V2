-- ============================================================================
-- perf/seed/seed_perf_dataset.sql
-- BookTown-V2 실행계획(EXPLAIN ANALYZE) 검증용 재현 가능한 시드 데이터셋
--
-- 목적
--   인덱스 유무에 따른 실행계획 차이가 실제로 관측될 만큼의 데이터를 만든다.
--   src/main/resources/db/schema.sql 의 실제 컬럼/제약을 그대로 따른다.
--
-- 주의
--   ⚠️ 반드시 전용 성능 테스트 DB에서만 실행할 것. 운영/개발 DB에서 실행 금지.
--   ⚠️ 이 스크립트는 booktown_perf 스키마에 schema.sql 이 이미 적재된 상태를 가정한다.
--
-- 실행
--   mysql -uroot booktown_perf < src/main/resources/db/schema.sql
--   mysql -uroot booktown_perf < perf/seed/seed_perf_dataset.sql
--
-- 데이터 분포 설계 의도
--   실제 서비스는 사용자별 활동량이 균등하지 않다(소수의 헤비 유저 + 다수의 라이트 유저).
--   균등 분포만 만들면 사용자당 행 수가 작아 정렬/랜덤 I/O 비용이 드러나지 않으므로,
--   의도적으로 heavy-tail 분포를 만들어 두 구간을 모두 측정할 수 있게 한다.
--     - HEAVY 사용자 (id 1..100)      : 사용자당 수천 건
--     - LIGHT 사용자 (id 101..20000)  : 사용자당 수십 건
-- ============================================================================

SET SESSION cte_max_recursion_depth = 4294967295;
SET SESSION unique_checks = 0;
SET SESSION foreign_key_checks = 1;   -- 참조 무결성은 유지한다(인덱스/실행계획 신뢰성 확보)

-- ---------------------------------------------------------------------------
-- 0. 숫자 헬퍼 테이블 (재귀 CTE 대신 cross join — 대량 생성 시 훨씬 빠르고 안정적)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS seq_helper;
CREATE TABLE seq_helper (n BIGINT NOT NULL PRIMARY KEY);

-- g 를 최상위 자리로 두어야 n 이 1..4,000,000 으로 빈틈 없이 연속된다.
-- (g 를 최하위 자리에 두면 g<4 때문에 끝자리 4~9 가 비어 40% 만 생성됨)
INSERT INTO seq_helper (n)
SELECT g.i * 1000000
     + ((((a.i * 10 + b.i) * 10 + c.i) * 10 + d.i) * 10 + e.i) * 10 + f.i
     + 1
FROM   (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) g;
-- => 4,000,000 rows (n = 1 .. 4000000)

-- ---------------------------------------------------------------------------
-- 1. users : 20,000명
-- ---------------------------------------------------------------------------
INSERT INTO users (id, email, password, nickname, profile_image_url, role, created_at)
SELECT n,
       CONCAT('perf-user-', n, '@booktown.local'),
       '$2a$10$seedseedseedseedseedseedseedseedseedseedseedseedseedseedse',
       CONCAT('퍼프유저', n),
       NULL,
       'USER',
       NOW() - INTERVAL (n % 365) DAY
FROM seq_helper WHERE n <= 20000;

-- ---------------------------------------------------------------------------
-- 2. book : 200,000권
--    검색(LIKE) 테스트가 의미를 가지려면 title/author 텍스트가 다양해야 한다.
--    실제 Gutendex 수집 도서와 비슷하게 "형용사 + 명사" 조합으로 제목을 만든다.
-- ---------------------------------------------------------------------------
INSERT INTO book (id, title, author, description, cover_image_url, genre, country, bookmark_count, has_content, created_at)
SELECT n,
       CONCAT(
         ELT(1 + (n % 12), 'The Silent','A Distant','The Golden','Winter','The Last','Crimson',
                            'The Hidden','Eternal','The Broken','Midnight','The Quiet','Scarlet'),
         ' ',
         ELT(1 + (FLOOR(n / 12) % 14), 'Garden','Harbour','Kingdom','Letters','Voyage','Mirror','Orchard',
                            'Republic','Sonata','Testament','Vineyard','Watchman','Lantern','Cathedral'),
         ' #', n
       ),
       CONCAT(
         ELT(1 + (n % 15), 'Jane','Charles','Leo','Fyodor','Emily','Herman','Mary','Oscar',
                            'Virginia','Franz','James','Edith','Thomas','Nathaniel','Louisa'),
         ' ',
         ELT(1 + (FLOOR(n / 15) % 13), 'Austen','Dickens','Tolstoy','Dostoevsky','Bronte','Melville','Shelley',
                            'Wilde','Woolf','Kafka','Joyce','Wharton','Hardy')
       ),
       CONCAT('Seed description for performance testing. Row ', n,
              '. This text exists to give the description column a realistic size.'),
       NULL,
       ELT(1 + (n % 5), 'NOVEL','POETRY','DRAMA','ESSAY','HISTORY'),
       ELT(1 + (n % 5), 'KOREA','CHINA','JAPAN','WESTERN','OTHER'),
       (n * 7919) % 500,
       IF(n % 3 = 0, TRUE, FALSE),
       NOW() - INTERVAL (n % 720) DAY
FROM seq_helper WHERE n <= 200000;

-- ---------------------------------------------------------------------------
-- 3. quiz : 200,000건 (quiz_submission 의 FK 대상)
--    quiz_job_id 는 NULL 허용이므로 quiz_job 없이 생성한다.
-- ---------------------------------------------------------------------------
INSERT INTO quiz (id, user_id, book_id, quiz_job_id, difficulty, question_count, created_at)
SELECT n,
       1 + (n % 20000),
       1 + (n % 200000),
       NULL,
       ELT(1 + (n % 3), 'EASY','NORMAL','HARD'),
       5 + (n % 16),
       NOW() - INTERVAL (n % 365) DAY
FROM seq_helper WHERE n <= 200000;

-- ---------------------------------------------------------------------------
-- 4. quiz_submission : 2,000,000건 (heavy-tail 분포)
--    후보 A 대상 쿼리:
--      WHERE user_id = ? ORDER BY submitted_at DESC LIMIT ?
--
--    4-1) LIGHT 구간 : 1,000,000건을 20,000명에게 균등 배분 → 사용자당 50건
-- ---------------------------------------------------------------------------
INSERT INTO quiz_submission (quiz_id, user_id, score, correct_count, total_count, submitted_at)
SELECT 1 + (n % 200000),
       1 + (n % 20000),
       (n * 37) % 101,
       (n * 37) % 11,
       10,
       NOW() - INTERVAL ((n * 13) % 525600) MINUTE   -- 최근 1년 범위로 분산
FROM seq_helper WHERE n <= 1000000;

--    4-2) HEAVY 구간 : 1,000,000건을 상위 100명에게 배분 → 사용자당 약 10,000건 추가
INSERT INTO quiz_submission (quiz_id, user_id, score, correct_count, total_count, submitted_at)
SELECT 1 + (n % 200000),
       1 + (n % 100),
       (n * 41) % 101,
       (n * 41) % 11,
       10,
       NOW() - INTERVAL ((n * 17) % 525600) MINUTE
FROM seq_helper WHERE n <= 1000000;

-- ---------------------------------------------------------------------------
-- 5. summary_job : 1,000,000건 (heavy-tail 분포)
--    후보 B 대상 쿼리:
--      SELECT COUNT(*) WHERE user_id = ? AND created_at > ?
--
--    5-1) LIGHT 구간 : 500,000건 / 20,000명 → 사용자당 25건
-- ---------------------------------------------------------------------------
INSERT INTO summary_job (user_id, book_id, status, summary_document_id, error_message, retryable, created_at, updated_at)
SELECT 1 + (n % 20000),
       1 + (n % 200000),
       ELT(1 + (n % 4), 'COMPLETED','COMPLETED','COMPLETED','FAILED'),
       IF(n % 4 = 3, NULL, UUID()),
       NULL,
       FALSE,
       NOW() - INTERVAL ((n * 11) % 525600) MINUTE,
       NOW() - INTERVAL ((n * 11) % 525600) MINUTE
FROM seq_helper WHERE n <= 500000;

--    5-2) HEAVY 구간 : 500,000건을 상위 100명에게 → 사용자당 약 5,000건 추가
INSERT INTO summary_job (user_id, book_id, status, summary_document_id, error_message, retryable, created_at, updated_at)
SELECT 1 + (n % 100),
       1 + (n % 200000),
       ELT(1 + (n % 4), 'COMPLETED','COMPLETED','COMPLETED','FAILED'),
       IF(n % 4 = 3, NULL, UUID()),
       NULL,
       FALSE,
       NOW() - INTERVAL ((n * 19) % 525600) MINUTE,
       NOW() - INTERVAL ((n * 19) % 525600) MINUTE
FROM seq_helper WHERE n <= 500000;

SET SESSION unique_checks = 1;

-- ---------------------------------------------------------------------------
-- 6. 통계 갱신 — 실행계획 측정 전 반드시 수행 (옵티마이저 추정치 신뢰성)
-- ---------------------------------------------------------------------------
ANALYZE TABLE users, book, quiz, quiz_submission, summary_job;

-- ---------------------------------------------------------------------------
-- 7. 적재 결과 확인
-- ---------------------------------------------------------------------------
SELECT 'users' t, COUNT(*) rows_cnt FROM users
UNION ALL SELECT 'book', COUNT(*) FROM book
UNION ALL SELECT 'quiz', COUNT(*) FROM quiz
UNION ALL SELECT 'quiz_submission', COUNT(*) FROM quiz_submission
UNION ALL SELECT 'summary_job', COUNT(*) FROM summary_job;
