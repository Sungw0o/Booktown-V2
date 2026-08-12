-- perf/seed/seed_books.sql
-- 부하 테스트용 더미 도서 대량 적재 (로컬 테스트 DB 전용)
-- ⚠️ 실제 `books` 테이블 컬럼명/NOT NULL 제약에 맞춰 컬럼 목록을 반드시 조정할 것.
--    (아래는 예시 스키마 가정: id AUTO_INCREMENT, title, author, genre, description, created_at)
-- MySQL 재귀 CTE 로 5,000건 생성.

INSERT INTO books (title, author, genre, description, created_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 5000
)
SELECT
  CONCAT('Perf Test Book ', n)                              AS title,
  CONCAT('Author ', 1 + (n % 300))                          AS author,
  ELT(1 + (n % 5), 'novel', 'poetry', 'drama', 'essay', 'history') AS genre,
  CONCAT('Seed description for load testing, row ', n)      AS description,
  NOW()                                                     AS created_at
FROM seq;

-- 적재 확인
-- SELECT COUNT(*) AS total, MIN(id) AS min_id, MAX(id) AS max_id FROM books;

-- 정리(테스트 후): 시드 도서만 삭제
-- DELETE FROM books WHERE title LIKE 'Perf Test Book %';
