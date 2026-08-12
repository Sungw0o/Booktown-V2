# [A] RDBMS 실행계획 기반 인덱스 튜닝

> 이 문서의 모든 수치는 실제 MySQL 8.0.46 인스턴스에서 측정한 값이다.
> 원본 출력은 `perf/results/explain/` 에 그대로 보관되어 있다.
> 추정치나 예상치는 포함하지 않았고, 개선 효과가 없었던 항목도 그대로 남겼다.

| 항목 | 값 |
|---|---|
| 측정 일시 | 2026-08-12 (UTC) |
| DBMS | MySQL 8.0.46 |
| `innodb_buffer_pool_size` | 512M |
| 측정 방식 | 워밍업 2회 후 `EXPLAIN ANALYZE` 15회 반복, 중앙값(p50) 사용 |
| 원본 데이터 | `perf/results/explain/BEFORE.txt`, `AFTER.txt`, `BENCH_*.txt` |
| 시드 스크립트 | `perf/seed/seed_perf_dataset.sql` |
| 마이그레이션 | `src/main/resources/db/migration/V202608120001__perf_indexes.sql` |

---

## 1. 문제 상황

BookTown 의 기존 부하 테스트 리포트(`docs/PERFORMANCE_TEST_REPORT.md`, 2026-07-09)는
도서 5,000건 기준으로 "뚜렷한 병목 없음(p95 40.8ms)" 이라고 결론지었다.

그러나 이 결론에는 두 가지 사각지대가 있었다.

1. **조회 API 만 측정했다.** 사용자별 이력 조회(퀴즈 이력)와 Job 생성 시 실행되는
   rate-limit 카운트 쿼리는 측정 대상이 아니었다.
2. **데이터가 균등 분포였고 규모가 작았다.** 실제 서비스는 소수의 헤비 유저가
   대부분의 활동을 만든다. 사용자당 행 수가 커질 때의 비용은 확인되지 않았다.

그래서 Repository 계층의 모든 쿼리를 조사해 **인덱스가 조회 패턴을 지원하지 못하는 지점**을
먼저 코드로 찾고, 실행계획으로 검증했다.

### 조사 결과 — 인덱스와 쿼리 패턴의 불일치

| # | Repository 메서드 | 쿼리 패턴 | 기존 인덱스 | 가설 |
|---|---|---|---|---|
| A | `QuizSubmissionRepository.findAllByUserIdOrderBySubmittedAtDesc` | `WHERE user_id=? ORDER BY submitted_at DESC LIMIT` | `(user_id)` 단일 | 정렬 컬럼이 인덱스에 없어 filesort |
| B | `SummaryJobRepository.countByUserIdAndCreatedAtAfter` | `WHERE user_id=? AND created_at>?` | `(user_id, book_id)` | `created_at` 이 인덱스에 없어 테이블 접근 후 필터 |
| C | `BookRepository.searchByTitleOrAuthor` | `title LIKE '%q%' OR author LIKE '%q%'` | 없음 | 선행 와일드카드 + OR → full scan |
| D | `IllustrationJobRepository.countByUserIdAndCreatedAtAfter` | B와 동일 | `(user_id)` 단일 | B와 동일 |

---

## 2. 기존 Query

Hibernate 가 실제로 생성하는 SQL 형태로 재구성했다.

```sql
-- A. 퀴즈 이력 조회 (Page 반환이므로 count 쿼리가 함께 실행된다)
SELECT qs.id, qs.correct_count, qs.quiz_id, qs.score,
       qs.submitted_at, qs.total_count, qs.user_id
FROM quiz_submission qs
WHERE qs.user_id = ?
ORDER BY qs.submitted_at DESC
LIMIT 20 OFFSET 0;

SELECT COUNT(qs.id) FROM quiz_submission qs WHERE qs.user_id = ?;

-- B. 요약 일일 생성 횟수 제한 (POST /books/{id}/summaries 요청마다 실행)
SELECT COUNT(sj.id) FROM summary_job sj
WHERE sj.user_id = ? AND sj.created_at > ?;

-- C. 도서 검색
SELECT b.* FROM book b
WHERE b.title LIKE '%q%' OR b.author LIKE '%q%'
ORDER BY b.created_at DESC
LIMIT 20 OFFSET 0;
```

---

## 3. 데이터 규모

실행계획 차이가 드러나도록 heavy-tail 분포로 시드했다.
균등 분포만으로는 사용자당 행 수가 작아 정렬/랜덤 I/O 비용이 나타나지 않기 때문이다.

| 테이블 | 행 수 | 데이터 | 인덱스 | 분포 |
|---|---:|---:|---:|---|
| `book` | 200,000 | 42.6 MB | 11.0 MB | — |
| `quiz_submission` | 2,000,000 | 123.7 MB | 134.3 MB | HEAVY(user 1~100) 10,050건 / LIGHT 50건 |
| `summary_job` | 1,000,000 | 94.6 MB | 104.8 MB | HEAVY 5,025건 / LIGHT 25건 |
| `illustration_job` | 500,000 | — | — | HEAVY 2,513건 / LIGHT 12~13건 |
| `users` | 20,000 | 3.5 MB | 3.0 MB | — |
| `quiz` | 200,000 | 12.5 MB | 16.5 MB | — |

**HEAVY / LIGHT 를 모두 측정한 이유**: 인덱스 효과는 사용자당 행 수에 비례한다.
한쪽만 측정하면 "항상 극적으로 빨라진다" 또는 "효과 없다" 중 하나로 잘못된 결론에 도달한다.

---

## 4. EXPLAIN ANALYZE — Before

### A. 퀴즈 이력 조회 (HEAVY user, 보유 10,050건)

```
EXPLAIN
type=ref  key=idx_quiz_submission_user_id  rows=17580  filtered=100.00  Extra=Using filesort

EXPLAIN ANALYZE
-> Limit: 20 row(s)  (cost=12661 rows=20) (actual time=14..14 rows=20 loops=1)
    -> Sort: qs.submitted_at DESC, limit input to 20 row(s) per chunk
         (cost=12661 rows=17580) (actual time=14..14 rows=20 loops=1)
        -> Index lookup on qs using idx_quiz_submission_user_id (user_id=7)
             (cost=12661 rows=17580) (actual time=2.39..13.4 rows=10050 loops=1)
```

15회 반복 측정: **p50 13.60ms / p95 54.80ms / max 154.00ms / avg 25.86ms**

### B. 요약 rate-limit 카운트 (HEAVY user, 보유 5,025건)

```
EXPLAIN
type=ref  key=idx_summary_job_user_book  rows=9318  filtered=33.33  Extra=Using where

EXPLAIN ANALYZE
-> Aggregate: count(sj.id)  (cost=6415 rows=1) (actual time=7.66..7.66 rows=1 loops=1)
    -> Filter: (sj.created_at > <cache>((now() - interval 1 day)))
         (cost=6105 rows=3106) (actual time=0.704..7.65 rows=16 loops=1)
        -> Index lookup on sj using idx_summary_job_user_book (user_id=7)
             (cost=6105 rows=9318) (actual time=0.695..7.48 rows=5025 loops=1)
```

15회 반복 측정: **p50 9.22ms / p95 54.30ms / max 58.60ms / avg 15.10ms**

### C. 도서 검색 (200,000건)

```
EXPLAIN
type=ALL  key=NULL  rows=197336  filtered=20.99  Extra=Using where; Using filesort

EXPLAIN ANALYZE
-> Limit: 20 row(s)  (cost=20415 rows=20) (actual time=86.1..86.1 rows=20 loops=1)
    -> Sort: b.created_at DESC, limit input to 20 row(s) per chunk
        -> Filter: ((b.title like '%Garden%') or (b.author like '%Garden%'))
             (actual time=0.0393..84.9 rows=14291 loops=1)
            -> Table scan on b  (cost=20415 rows=197336) (actual time=0.0367..47.6 rows=200000 loops=1)
```

목록 쿼리 78.65ms + count 쿼리 72.23ms ≈ **요청당 약 150ms**

### D. 삽화 rate-limit 카운트 (HEAVY user, 보유 2,513건)

```
type=ref  key=idx_illustration_job_user_id  rows=2513  filtered=33.33  Extra=Using where
→ Index lookup 2,513행 → Filter 후 9행.  p50 2.60ms / p95 10.70ms
```

---

## 5. 실행계획 분석 — 왜 비효율적인가

### A. `Using filesort` 의 실제 의미

`rows=17580`, `filtered=100.00` 만 보면 "인덱스를 잘 타고 있다"고 오해하기 쉽다.
문제는 `Extra` 의 `Using filesort` 다.

인덱스 `(user_id)` 는 user_id 로 행을 **찾을** 수는 있지만, 그 안에서 `submitted_at` 순서를
보장하지 못한다. 따라서 MySQL 은

1. user_id=7 인 행을 **전부**(실측 10,050행) 읽고
2. 메모리에서 `submitted_at DESC` 로 정렬한 뒤
3. 상위 20행만 반환한다

즉 **20행을 얻기 위해 10,050행을 읽고 정렬**한다. `LIMIT 20` 이 비용을 줄여주지 못한다.
사용자의 퀴즈 이력이 쌓일수록 비용이 선형으로 증가한다.

max 154ms 라는 꼬리 지연도 여기서 나온다. 정렬 버퍼(`sort_buffer_size`)를 넘어서면
디스크 임시 파일을 쓰기 때문이다.

### B. 인덱스는 탔지만 테이블을 다시 읽는 경우

`filtered=33.33` 이 핵심 단서다. 옵티마이저가 "인덱스로 찾은 행 중 33%만 조건을 통과할 것"으로
추정했다는 뜻이고, 실제로는 5,025행 중 16행만 통과했다.

`idx_summary_job_user_book (user_id, book_id)` 에는 `created_at` 이 없다.
그래서 MySQL 은 user_id 로 5,025개 인덱스 엔트리를 찾은 뒤,
**각 엔트리마다 PK 로 테이블 행을 다시 읽어**(random I/O) `created_at` 을 확인해야 한다.
5,025번의 랜덤 접근이 7ms 의 정체다.

이 쿼리는 요약 생성 API 가 호출될 때마다 실행되는 rate-limit 검사다.
AI Job 생성 경로 위에 있으므로 지연이 사용자 대기 시간에 직접 더해진다.

### C. 선행 와일드카드가 인덱스를 무력화한다

B-Tree 인덱스는 **왼쪽부터** 정렬되어 있어 접두어 탐색만 가능하다.
`LIKE 'Garden%'` 은 인덱스 범위 탐색이 되지만 `LIKE '%Garden%'` 은
어디서 시작할지 알 수 없으므로 전체를 확인해야 한다.
게다가 `OR` 로 두 컬럼을 묶어 index merge 도 불가능하다.

결과적으로 `type=ALL`(full table scan)이며, 여기에 `created_at` 정렬을 위한 filesort 가 더해진다.
`Page` 반환이라 count 쿼리도 같은 full scan 을 한 번 더 수행한다.

---

## 6. 원인 정리

| # | 근본 원인 | 실행계획 증거 |
|---|---|---|
| A | 정렬 컬럼이 인덱스에 없음 → 전체 조회 후 메모리 정렬 | `Extra: Using filesort`, actual rows 10,050 → 20 |
| B | 범위 조건 컬럼이 인덱스에 없음 → 인덱스 탐색 후 테이블 랜덤 접근 | `filtered=33.33`, Filter 단계 5,025 → 16 |
| C | 선행 와일드카드 + OR → 인덱스 사용 불가 | `type=ALL`, `key=NULL`, rows=197,336 |
| D | B와 동일 | `filtered=33.33`, 2,513 → 9 |

---

## 7. 인덱스 설계 과정

### 7-1. 컬럼 순서 — 원칙만으로 정하지 않고 실측으로 검증했다

**원칙: Equality → Range → Sort**

- A: `user_id` 는 등치(Equality), `submitted_at` 은 정렬(Sort) → `(user_id, submitted_at)`
- B: `user_id` 는 등치(Equality), `created_at` 은 범위(Range) → `(user_id, created_at)`

원칙이 맞는지 **반대 순서 인덱스를 실제로 만들어** 비교했다.

**A — quiz_submission (HEAVY user, 15회 p50)**

| 인덱스 | p50 | max | 실행계획 |
|---|---:|---:|---|
| `(user_id)` 단일 (기존) | 13.60ms | 154.00ms | `Using filesort`, 10,050행 정렬 |
| **`(user_id, submitted_at)`** | **2.69ms** | 3.18ms | `Backward index scan`, 20행만 읽음 |
| `(user_id, submitted_at DESC)` | 2.85ms | 3.89ms | 정방향 스캔 |
| `(submitted_at, user_id)` ← 반대 순서 | 5.75ms | 6.57ms | 전체 인덱스 스캔 + Filter |

**B — summary_job (HEAVY user, 15회 p50)**

| 인덱스 | p50 | 실행계획 |
|---|---:|---|
| `(user_id, book_id)` (기존) | 9.22ms | 5,025행 읽고 16행 필터 |
| **`(user_id, created_at)`** | **0.034ms** | `Covering index range scan`, 16행 |
| `(created_at, user_id)` ← 반대 순서 | 0.608ms | 2,866행 스캔 후 필터 |

두 경우 모두 **반대 순서가 실제로 느렸다.** 이유도 실행계획에 그대로 나온다.
`(created_at, user_id)` 는 `created_at` 범위(최근 1일)로 먼저 좁혀지므로
그 안의 모든 사용자 행 2,866건을 스캔한 뒤 user_id 를 필터링한다.
등치 조건을 앞에 두어야 탐색 시작점이 한 점으로 고정된다.

### 7-2. ASC / DESC — 명시적 DESC 인덱스는 불필요했다

MySQL 8.0 은 `Backward index scan` 으로 오름차순 인덱스를 역방향으로 읽을 수 있다.
실측 결과 `(user_id, submitted_at)` 2.69ms vs `(user_id, submitted_at DESC)` 2.85ms 로
**유의미한 차이가 없었다.** 정렬 방향이 하나뿐이므로 기본(ASC)을 선택했다.

> DESC 인덱스가 의미 있는 경우는 `ORDER BY a ASC, b DESC` 처럼
> **컬럼마다 방향이 섞일 때**다. 이 쿼리는 해당하지 않는다.

### 7-3. Covering Index 여부

- **B, D 는 covering index 다.** `Extra: Using index`, 실행계획에 `Covering index range scan`.
  `COUNT(id)` 에 필요한 컬럼(`user_id`, `created_at`, 그리고 InnoDB 세컨더리 인덱스 리프에
  자동 포함되는 PK `id`)이 모두 인덱스 안에 있어 **테이블 접근이 0회**다.
  B의 개선폭이 271배로 특히 큰 이유가 이것이다.

- **A 는 covering index 가 아니다.** SELECT 목록에 `score`, `correct_count` 등이 있어
  20행에 대해서는 PK 로 테이블을 읽는다. 다만 **10,050행이 아니라 20행**이므로 충분하다.
  covering 으로 만들려면 5개 컬럼을 더 넣어야 하는데, 인덱스가 커져 쓰기 비용과
  버퍼 풀 점유가 늘어난다. **20행 랜덤 접근을 감수하는 편이 낫다고 판단했다.**

  참고로 A 의 count 쿼리는 변경 전에도 후에도 covering 이다(`Using index`).
  두 인덱스를 동시에 두고 `FORCE INDEX` 로 공정 비교한 결과 1.790ms vs 1.840ms 로
  사실상 차이가 없었다(10장 참조).

### 7-4. 중복 인덱스 제거 — 테이블마다 다르게 판단했다

복합 인덱스 `(user_id, X)` 가 생기면 `(user_id)` 단일 인덱스는 **좌측 프리픽스**라서
논리적으로는 중복이다. 하지만 실제로 지워도 되는지는 테이블마다 확인해야 했다.

**`quiz_submission` — 삭제함 ✅**

`quiz_submission` 을 참조하는 모든 쿼리에 대해 3가지 상태로 실행계획을 비교했다.

| 쿼리 | 단일만 | 단일+복합 | 복합만 |
|---|---:|---:|---:|
| `exists(quiz_id, user_id)` | 0.042ms (`idx_..._quiz_id` 사용) | 0.036ms | 0.036ms (동일 인덱스) |
| `count(user_id)` | 1.26ms (`Using index`) | 1.22ms | 1.22ms (`Using index` 유지) |

회귀가 없음을 확인하고 `idx_quiz_submission_user_id` 를 삭제했다.

**`illustration_job` — 삭제하지 않음 ❌**

같은 논리로 접근했다가 실행계획에서 반대 결과를 발견했다.

```
EXPLAIN (existsByUserIdAndSceneIdAndStatusIn)
type=index_merge
key=idx_illustration_job_scene_id,idx_illustration_job_user_id
Extra=Using intersect(idx_illustration_job_scene_id,idx_illustration_job_user_id); Using where
```

이 쿼리는 `scene_id` 인덱스와 `user_id` 인덱스를 **index_merge intersect** 로 함께 사용한다.
`user_id` 단일 인덱스를 지우면 이 최적화가 깨진다. 그래서 유지했다.

> **"복합 인덱스가 있으면 단일 인덱스는 항상 지워도 된다"는 명제는 거짓이다.**
> 좌측 프리픽스 규칙은 *단독 사용* 기준이고, index_merge 는 별개 인덱스 2개를 요구한다.
> 테이블마다 실행계획을 확인해야 한다.

### 7-5. 도서 검색 — 측정 후 "이번에는 적용하지 않음" 으로 결론

FULLTEXT 를 실제로 만들어 비교했고, 그 결과 **적용하지 않기로 판단**했다.

**(1) 효과가 검색어 선택도에 따라 정반대였다**

| 매치 건수 | LIKE | ngram FULLTEXT | 결과 |
|---:|---:|---:|---|
| 1건 | 75.35ms | 10.06ms | ngram 7.5배 빠름 |
| 11건 | 74.58ms | 7.43ms | ngram 10배 빠름 |
| 14,280건 | 73.09ms | 137.96ms | **ngram 1.9배 느림** |
| 14,291건 | 74.45ms | 158.32ms | **ngram 2.1배 느림** |

LIKE 는 매치 건수와 무관하게 항상 ~75ms(전체 스캔 고정 비용)인 반면,
ngram 은 매치된 문서를 모두 스코어링해야 해서 광범위 매치에서 오히려 느려진다.

**(2) 검색 결과 정합성이 달라진다** — 이것이 결정적이었다

| 검색어 | LIKE 결과 | ngram 결과 | 문제 |
|---|---:|---:|---|
| `Garden` | 14,291 | 14,291 | 일치 ✅ |
| `개츠비` | 2 | 2 | 일치 ✅ |
| `Gard` | 14,291 | **54,938** | false positive (bigram 인접성 미보장) |
| `a` (1글자) | 192,740 | **0** | `ngram_token_size=2` 미만 검색 불가 |

기본 파서(비-ngram)도 검토했으나 한국어에서 더 나빴다.
`개츠비` 검색 시 LIKE 는 2건, 기본 파서는 1건 — `개츠비의` 가 별도 토큰이 되어 매칭에 실패한다.
한국어 서비스에서는 채택할 수 없다.

**(3) 현재 데이터 규모에서는 병목이 아니다**

| book 행 수 | 목록 쿼리 | count 쿼리 | 실행계획 |
|---:|---:|---:|---|
| 5,000 | 2.17ms | 2.12ms | `type=ALL`, rows=5,044 |
| 50,000 | 16.48ms | 15.46ms | `type=ALL`, rows=49,385 |
| 200,000 | 64.42ms | 60.63ms | `type=ALL`, rows=197,537 |

비용이 행 수에 **정확히 정비례**한다(약 0.32ms / 1,000행).
현재 운영 도서 수는 약 5,000건이므로 검색 총비용은 4.3ms 수준이다.

**결론**: 지금 FULLTEXT 를 넣으면 이득 없이 검색 의미만 바꾼다.
**도서 수 5만 건 도달 시 재검토**하기로 하고, 근거와 함께 마이그레이션 파일에 기록했다.
그 시점에도 1글자 검색과 부분어 검색을 API 계약에서 어떻게 다룰지 먼저 정해야 한다.

---

## 8. 변경 내용

`src/main/resources/db/migration/V202608120001__perf_indexes.sql`

```sql
-- A. filesort 제거
CREATE INDEX idx_quiz_submission_user_submitted ON quiz_submission (user_id, submitted_at);
DROP INDEX idx_quiz_submission_user_id ON quiz_submission;   -- 좌측 프리픽스 중복, 회귀 없음 확인

-- B. covering index 전환
CREATE INDEX idx_summary_job_user_created ON summary_job (user_id, created_at);
-- idx_summary_job_user_book 은 중복 Job 방지 쿼리가 사용하므로 유지

-- D. covering index 전환
CREATE INDEX idx_illustration_job_user_created ON illustration_job (user_id, created_at);
-- idx_illustration_job_user_id 는 index_merge intersect 에 사용되므로 유지

ANALYZE TABLE quiz_submission, summary_job, illustration_job;
```

함께 변경한 파일
- `src/main/resources/db/schema.sql` — 신규 DB 생성 시에도 동일한 인덱스가 만들어지도록
- `QuizSubmission`, `SummaryJob`, `IllustrationJob` 엔티티의 `@Index` — `ddl-auto=update` 인
  local 프로파일에서 삭제한 인덱스가 되살아나지 않도록 (prod 는 `validate`)

> `schema.sql` 로 만든 DB 의 인덱스 목록과 측정에 사용한 DB 의 인덱스 목록을
> `information_schema.STATISTICS` 로 비교해 **완전 일치**함을 확인했다.
> 즉 이 문서의 After 수치는 최종 `schema.sql` 상태에서 나온 값이다.

---

## 9. EXPLAIN ANALYZE — After

### A. 퀴즈 이력 조회 (HEAVY user)

```
EXPLAIN
type=ref  key=idx_quiz_submission_user_submitted  rows=19112  Extra=Backward index scan
                                                              ^^^^^^^^ filesort 사라짐

EXPLAIN ANALYZE
-> Limit: 20 row(s)  (cost=19614 rows=20) (actual time=2.65..2.66 rows=20 loops=1)
    -> Index lookup on qs using idx_quiz_submission_user_submitted (user_id=7) (reverse)
         (cost=19614 rows=19112) (actual time=2.65..2.66 rows=20 loops=1)
```

**actual rows 가 10,050 → 20 으로 줄었다.** 정렬 단계 자체가 실행계획에서 사라졌다.

### B. 요약 rate-limit 카운트 (HEAVY user)

```
EXPLAIN
type=range  key=idx_summary_job_user_created  rows=16  filtered=100.00
Extra=Using where; Using index
                   ^^^^^^^^^^^ covering index

EXPLAIN ANALYZE
-> Aggregate: count(sj.id)  (cost=5.85 rows=1) (actual time=0.0206..0.0207 rows=1 loops=1)
    -> Filter: ((sj.user_id = 7) and (sj.created_at > <cache>((now() - interval 1 day))))
         (cost=4.25 rows=16) (actual time=0.0154..0.0183 rows=16 loops=1)
        -> Covering index range scan on sj using idx_summary_job_user_created
             over (user_id = 7 AND '2026-08-11 10:42:37' < created_at)
             (cost=4.25 rows=16) (actual time=0.0133..0.0152 rows=16 loops=1)
```

**`type` 이 `ref` → `range` 로, 읽는 행이 5,025 → 16 으로 줄었고 테이블 접근이 0회가 되었다.**

---

## 10. 최종 개선 수치

각 15회 반복 측정, `EXPLAIN ANALYZE` 최상위 노드 actual time 기준.

> **측정 편차에 대한 주의**: 측정 환경이 2 vCPU 샌드박스라 실행마다 편차가 있었다.
> A 계열은 After 를 3회 독립 실행했고, 아래 표에는 **그 범위를 그대로 적었다.**
> 단일 최적값만 고르지 않았다.
> (원본: `BENCH_index_variants.txt`, `BENCH_A_after_final.txt`, `BENCH_A_after_final2.txt`)

| 쿼리 | 사용자 | Before p50 | After p50 | 배수 | Before 읽은 행 | After 읽은 행 |
|---|---|---:|---:|---:|---:|---:|
| 퀴즈 이력 1페이지 | HEAVY (10,050건) | 13.60ms | **2.06 ~ 3.02ms** | **약 4.5~6.6배** | 10,050 | 20 |
| 퀴즈 이력 1페이지 | LIGHT (50건) | 0.133ms | 0.145 ~ 0.185ms | **개선 없음 (소폭 느려짐)** | 50 | 20 |
| 퀴즈 이력 count | HEAVY | 1.79ms | 1.84ms | 변화 없음 (노이즈 범위) | 10,050 | 10,050 |
| 퀴즈 이력 OFFSET 5000 | HEAVY | 14.30ms | 8.09 ~ 9.72ms | 약 1.5~1.8배 | 10,050 | 5,020 |
| 요약 rate-limit | HEAVY (5,025건) | 9.22ms | **0.034ms** | **271배** | 5,025 | 16 |
| 요약 rate-limit | LIGHT (25건) | 0.100ms | 0.021ms | 4.8배 | 25 | 0 |
| 삽화 rate-limit | HEAVY (2,513건) | 2.60ms | **0.026ms** | **100배** | 2,513 | 9 |
| 도서 검색 | — | 78.65ms | 변경 없음 | — | 200,000 | 200,000 |

### 개선되지 않은 항목도 그대로 기록한다

**1. LIGHT 사용자의 퀴즈 이력은 오히려 소폭 느려졌다 (0.133ms → 0.145~0.185ms).**
복합 인덱스는 단일 인덱스보다 엔트리가 넓어(`user_id` + `submitted_at` + PK) 같은 50행을 읽어도
읽는 페이지가 조금 더 많다. 절대값이 0.05ms 수준이라 실사용 영향은 없지만,
**"인덱스를 추가하면 모든 경우가 빨라진다"가 사실이 아님을 보여주는 실측 사례**다.

**2. 퀴즈 이력 count 쿼리는 변화가 없다.**
Before/After 를 별도 실행으로 비교하면 노이즈가 섞이므로, 두 인덱스를 동시에 두고
`FORCE INDEX` 로 같은 시점에 비교했다.

| 인덱스 | p50 (25회) | 실행계획 |
|---|---:|---|
| `(user_id)` 단일 | 1.790ms | `Using index` (covering) |
| `(user_id, submitted_at)` 복합 | 1.840ms | `Using index` (covering) |

차이 2.8%로 노이즈 범위다. 둘 다 covering index 이고 `COUNT` 는 어차피 10,050개
엔트리를 모두 세야 하므로 인덱스 구성이 바뀌어도 개선될 여지가 없다.
**이 쿼리는 애초에 개선 대상이 아니었고, 중요한 것은 "느려지지 않았다"는 점이다.**

### 꼬리 지연

| 쿼리 | Before p95 / max | After p95 / max |
|---|---:|---:|
| 퀴즈 이력 (HEAVY) | 54.80 / 154.00ms | 3.04 / 3.18ms (1차)<br>8.88 / 8.96ms (2차)<br>12.20 / 13.30ms (3차) |
| 요약 rate-limit (HEAVY) | 54.30 / 58.60ms | 0.049 / 0.058ms |

Before 의 max 154ms 는 정렬 버퍼를 넘겨 디스크 임시 파일을 쓴 경우다.
After 에서도 p95 가 실행마다 3~12ms 로 흔들렸는데, 이는 **인덱스 문제가 아니라
2 vCPU 샌드박스의 배경 부하** 때문으로 보인다(같은 시점 `SELECT 1` 도 함께 튀었다).
**After 의 p95 를 "3.04ms" 라고 단정하지 않고 세 실행을 모두 남긴 이유다.**

### 회귀 검증 — 개선 대상이 아닌 쿼리가 느려지지 않았는가

| 쿼리 | Before p50 | After p50 | 판정 |
|---|---:|---:|---|
| 중복 Job exists (`user_id`, `book_id`) | 0.014ms | 0.007ms | 회귀 없음 (동일 인덱스 계속 사용) |
| 퀴즈 제출 exists (`quiz_id`, `user_id`) | 0.042ms | 0.036ms | 회귀 없음 |
| 퀴즈 이력 count | 1.790ms | 1.840ms | 회귀 없음 (covering 유지, 노이즈 범위) |
| 삽화 중복 exists (index_merge) | 0.519ms | 0.529ms | 회귀 없음 (단일 인덱스 유지 결정 덕분) |

### k6 Before/After

**미측정.** 이 문서의 수치는 SQL 레벨 측정이다.
API 레벨 p95 개선은 전체 스택(MySQL + MongoDB + Redis + Chroma)을 띄우고
`perf/RUNBOOK-transaction-boundary.md` 절차로 별도 측정해야 한다.

**SQL 레벨 개선이 API p95 에 그대로 반영되지는 않는다.** 요청 시간에는 네트워크,
직렬화, JWT 검증 등이 포함되기 때문이다. 다만 rate-limit 쿼리 9.22ms → 0.034ms 는
AI Job 생성 API 의 응답 시간에서 그만큼이 직접 사라지는 값이다.

---

## 11. 트레이드오프

**1. 쓰기 비용이 증가한다**
`summary_job`, `illustration_job` 은 인덱스가 하나씩 늘었다.
INSERT/UPDATE 마다 B-Tree 갱신이 추가된다. 다만 두 테이블 모두
**Job 생성 시 1회 INSERT + 상태 전이 2회 UPDATE** 가 전부인 저빈도 쓰기 테이블이고,
`created_at` 은 생성 후 변하지 않아 인덱스 재정렬이 발생하지 않는다.
`quiz_submission` 은 인덱스 1개를 추가하고 1개를 삭제해 **개수가 동일**하다.

**2. 저장 공간이 증가한다**
`(user_id, created_at)` 은 `(user_id)` 보다 컬럼이 하나 많아 인덱스가 커진다.
버퍼 풀에 올라가는 페이지 수가 늘어 다른 데이터의 캐시 적중률에 영향을 줄 수 있다.
측정 환경 기준 `quiz_submission` 인덱스 총량은 134.3MB → 135.3MB 로 거의 동일했다
(하나 추가 + 하나 삭제이므로).

**3. LIGHT 사용자는 이득이 없고 오히려 소폭 느려졌다**
퀴즈 이력 LIGHT 사용자는 0.133ms → 0.145~0.185ms 로 **약간 느려졌다.**
복합 인덱스 엔트리가 넓어져 같은 50행을 읽는 데 페이지가 조금 더 필요하기 때문이다.
절대값이 0.05ms 수준이라 실사용 영향은 없지만, 이 변경의 가치가
**평균 사용자가 아니라 헤비 사용자와 꼬리 지연에만 있다**는 점은 분명히 해 둔다.

**4. 도서 검색은 여전히 O(n) 이다**
의도적으로 남겨둔 부채다. 도서 수 증가에 따라 선형으로 느려진다.
5만 건 도달 시 재검토해야 하며, 그 판단 근거는 이미 측정해 두었다.

**5. 측정 환경이 운영 환경과 다르다**
샌드박스 MySQL 8.0.46 / buffer pool 512M 단독 인스턴스에서 측정했다.
운영은 MySQL 8.4.5 이고 동시 부하가 있다. **절대 수치는 다를 수 있다.**
다만 실행계획의 변화(`Using filesort` 제거, `Using index` 획득, 읽는 행 수 감소)는
환경과 무관한 구조적 개선이다.

---

## 12. 면접 예상 질문

### Q1. 왜 이 인덱스 순서인가?

`(user_id, created_at)` 은 **Equality → Range** 원칙을 따른 것이다.
`user_id = ?` 는 등치, `created_at > ?` 는 범위 조건이다.
등치 조건을 앞에 두면 B-Tree 탐색 시작점이 한 점으로 고정되고, 그 안에서 `created_at` 이
정렬되어 있으니 범위 조건이 스캔 구간을 좁힌다.

원칙만 믿지 않고 반대 순서를 실제로 만들어 검증했다.
`(created_at, user_id)` 는 p50 0.608ms 로 18배 느렸고, 실행계획을 보면
최근 1일에 해당하는 전체 사용자 행 2,866건을 스캔한 뒤 user_id 를 필터링하고 있었다.

### Q2. 단일 인덱스 여러 개와 복합 인덱스의 차이는?

단일 인덱스 `(user_id)` 와 `(created_at)` 을 각각 만들면, MySQL 은 보통 **하나만** 선택하고
나머지 조건은 테이블을 읽어 필터링한다. index_merge 로 둘을 합칠 수도 있지만
두 인덱스에서 각각 PK 목록을 뽑아 교집합을 구하는 추가 비용이 든다.

복합 인덱스 `(user_id, created_at)` 은 두 조건을 **하나의 B-Tree 탐색**으로 해결하고,
이 경우엔 covering index 까지 되어 테이블 접근이 0회가 된다. 그래서 271배 차이가 났다.

다만 복합 인덱스는 **좌측 프리픽스**로만 쓸 수 있다. `(user_id, created_at)` 은
`WHERE user_id=?` 에는 쓰이지만 `WHERE created_at>?` 단독에는 쓰이지 않는다.

### Q3. 인덱스가 많으면 어떤 문제가 생기는가?

- **쓰기 비용**: INSERT/UPDATE/DELETE 마다 모든 관련 인덱스의 B-Tree 를 갱신해야 한다.
- **저장 공간과 버퍼 풀**: 인덱스가 버퍼 풀을 점유해 실제 데이터의 캐시 적중률이 떨어진다.
- **옵티마이저 오판**: 후보가 많아지면 통계가 부정확할 때 잘못된 인덱스를 고를 확률이 커진다.
- **잠금 경합**: 인덱스마다 갭 락이 걸릴 수 있다.

그래서 이번에 인덱스를 추가하면서 **중복이 된 `idx_quiz_submission_user_id` 는 삭제**했고,
삭제 전에 해당 테이블의 모든 쿼리 실행계획을 확인해 회귀가 없음을 검증했다.

### Q4. Cardinality 는 고려했는가?

고려했고, 그래서 **HEAVY / LIGHT 두 구간을 나눠 측정했다.**

`user_id` 는 20,000개 고유값으로 카디널리티가 높지만, **분포가 균등하지 않다.**
헤비 사용자 100명이 사용자당 10,050건, 나머지는 50건을 갖는다.
평균만 보면 사용자당 100건이지만 실제 병목은 헤비 사용자 구간에서 발생한다.

측정 결과가 이를 뒷받침한다. HEAVY 는 13.60 → 2.06~3.02ms(약 4.5~6.6배)로 개선됐지만
LIGHT 는 0.133 → 0.145~0.185ms 로 **오히려 소폭 느려졌다.**
**평균 데이터로만 측정했다면 "효과 없음" 또는 "오히려 악화"라는 잘못된 결론에 도달했을 것이다.**
반대로 헤비 유저만 봤다면 "모든 사용자가 5배 빨라졌다"고 과장했을 것이다.

### Q5. Covering Index 인가?

**쿼리마다 다르다.**

- `summary_job`, `illustration_job` 의 rate-limit COUNT 는 covering 이다.
  `Extra: Using index`, 실행계획에 `Covering index range scan`.
  `COUNT` 에 필요한 컬럼이 모두 인덱스에 있고, InnoDB 세컨더리 인덱스 리프에는
  PK 가 자동 포함되므로 `COUNT(id)` 도 인덱스만으로 해결된다. 이것이 271배의 원인이다.

- `quiz_submission` 의 이력 조회는 covering 이 **아니다.**
  SELECT 에 `score`, `correct_count`, `total_count` 등이 있어 20행은 PK 로 테이블을 읽는다.
  covering 으로 만들려면 컬럼 5개를 더 넣어야 하는데, 인덱스가 커져 쓰기 비용과
  버퍼 풀 점유가 늘어난다. **20행 랜덤 접근은 감수할 만하다고 판단했다.**
  10,050행이 아니라 20행이기 때문이다.

### Q6. Using filesort 는 항상 나쁜가?

아니다. **정렬 대상 행 수가 중요하다.**

같은 쿼리, 같은 `Using filesort` 인데 LIGHT 사용자(50행 정렬)는 0.133ms 로 문제가 없었고
HEAVY 사용자(10,050행 정렬)는 p50 13.60ms, max 154ms 였다.

`filesort` 는 "인메모리 퀵소트"를 포함하는 넓은 표현이라 소량이면 저렴하다.
문제가 되는 지점은 정렬 대상이 `sort_buffer_size` 를 넘어 **디스크 임시 파일**을 쓸 때이고,
Before 측정의 max 154ms 라는 꼬리가 정확히 그 증상이다.

그래서 filesort 를 봤다고 바로 인덱스를 추가하지 않고, `EXPLAIN ANALYZE` 의
actual rows 와 반복 측정의 p95/max 를 먼저 확인했다.

### Q7. 데이터가 적을 때 성능 차이가 없는 이유는?

인덱스의 이득은 **읽지 않아도 되는 행의 수**에서 나오는데, 데이터가 적으면 줄일 행이 없다.

실측 근거가 두 가지 있다.

1. 퀴즈 이력 LIGHT 사용자(50건): 0.133ms → 0.145~0.185ms. 50행 정렬은 원래 저렴해서
   없앨 비용이 없었고, 인덱스가 넓어진 만큼 오히려 미세하게 손해였다.
2. 도서 검색: 5,000건에서 full table scan 이 2.17ms 다. 5,000행이면 페이지 몇 개라
   대부분 버퍼 풀에 있고, 인덱스를 타도 크게 나아지지 않는다.
   같은 쿼리가 50,000건에서 16.48ms, 200,000건에서 64.42ms 로 **정확히 선형 증가**했다.

이것이 기존 부하 테스트(도서 5,000건, p95 40.8ms)에서 "병목 없음" 결론이 나온 이유다.
**테스트 데이터 규모가 실제 서비스 규모를 반영하지 못하면 병목은 측정에서 사라진다.**
그래서 이번에는 시드 규모와 분포를 먼저 설계했다.

### Q8. Offset pagination 문제는 없는가?

**있다. 완전히 해결되지 않았고, 의도적으로 남겼다.**

`OFFSET 5000` 을 실측했다.

| | Before | After |
|---|---:|---:|
| OFFSET 0 | 13.60ms | 2.06 ~ 3.02ms |
| OFFSET 5000 | 14.30ms | 8.09 ~ 9.72ms |

After 에서 정렬은 사라졌지만 실행계획은 여전히 `actual rows 5020` 이다.
MySQL 은 건너뛸 5,000행도 **읽어야만** 건너뛸 수 있기 때문이다.
OFFSET 이 커질수록 선형으로 느려진다.
OFFSET 0 은 6배 가까이 개선됐는데 OFFSET 5000 은 1.5~1.8배에 그친 이유가 이것이다.
**인덱스로는 OFFSET 문제를 해결할 수 없다.**

근본 해법은 커서 기반(no-offset) 페이지네이션이다.

```sql
WHERE user_id = ? AND submitted_at < :lastSeenSubmittedAt
ORDER BY submitted_at DESC LIMIT 20
```

`(user_id, submitted_at)` 인덱스가 이 형태를 그대로 지원하므로 OFFSET 과 무관하게 일정하다.

이번에 적용하지 않은 이유는 **API 응답 계약이 바뀌기 때문**이다.
현재 `Page` 는 `totalElements`, `totalPages` 를 내려주고 프론트가 페이지 번호 UI 를 쓴다.
커서 방식은 총 개수를 제공하지 않아 프론트엔드 변경이 동반된다.
또한 퀴즈 이력에서 사용자가 250페이지까지 넘길 개연성이 낮아,
**측정된 위험(OFFSET 5000 에서 8~10ms)에 비해 변경 범위가 크다**고 판단했다.

다만 `submitted_at` 이 중복될 수 있으므로 커서 방식 도입 시에는
`(submitted_at, id)` 복합 커서를 써야 순서가 안정적이라는 점까지 확인해 두었다.
