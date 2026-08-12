# 퀴즈 이력 복합 인덱스 재현 보고서

## 문제 상황

사용자의 최근 퀴즈 제출 20건을 조회하는 쿼리는 `user_id`로 필터링한 뒤 `submitted_at DESC`로 정렬합니다. `user_id` 단일 인덱스만 있으면 HEAVY 사용자의 후보 행을 모두 읽고 정렬해야 합니다.

```sql
SELECT id, quiz_id, user_id, score, correct_count, total_count, submitted_at
FROM quiz_submission
WHERE user_id = 42
ORDER BY submitted_at DESC
LIMIT 20;
```

## 의사 결정

- `user_id` 단일 인덱스는 필터링만 지원하고 최신순 정렬을 처리하지 못합니다.
- `(submitted_at, user_id)`는 전체 최신순 탐색에는 유리하지만 사용자 필터가 선두 컬럼이 아닙니다.
- 실제 조회 조건과 정렬 순서를 함께 만족하는 `(user_id, submitted_at DESC)`를 선택했습니다.

## 측정 조건

| 항목 | 값 |
|---|---|
| 실행 일자 | 2026-08-13 |
| DB | MySQL 8.4.10 (`mysql:8.4`) |
| 실행 환경 | Docker Desktop, x86_64, 12 CPU, 15.44 GiB memory |
| 전체 데이터 | 500,000건 |
| HEAVY 사용자 | `user_id=42`, 100,000건 |
| 반환 행 | 20건 |
| 반복 횟수 | 인덱스 전후 각 5회 |
| 확인 방법 | MySQL `EXPLAIN ANALYZE` |

데이터 생성과 스키마는 [재현 SQL](../performance/mysql/quiz-history-index/setup-before.sql)에서 확인할 수 있습니다.

## 해결

기존 `user_id` 단일 인덱스를 실제 엔티티에 선언된 복합 인덱스로 교체했습니다.

```sql
ALTER TABLE quiz_submission
    DROP INDEX idx_quiz_submission_user_id,
    ADD INDEX idx_quiz_submission_user_submitted_at (user_id, submitted_at DESC);
```

## 검증 결과

| 지표 | Before | After |
|---|---:|---:|
| 실제 읽은 행 | 100,000 | 20 |
| 실행계획의 별도 Sort | 있음 | 없음 |
| 5회 중앙값 | 66.7ms | 2.02ms |
| 최소~최대 | 60.8~76.7ms | 1.69~2.15ms |

- 읽은 행은 5,000분의 1로 감소했습니다.
- 중앙값 기준 실행시간은 약 97.0% 감소했습니다.
- [Before 원문](../performance/mysql/quiz-history-index/results/before-explain-analyze.txt)
- [After 원문](../performance/mysql/quiz-history-index/results/after-explain-analyze.txt)

## 재현 순서

```powershell
docker run --rm -d --name booktown-index-evidence `
  -e MYSQL_ROOT_PASSWORD=booktown-evidence -p 13306:3306 mysql:8.4

Get-Content -Raw performance/mysql/quiz-history-index/setup-before.sql |
  docker exec -i booktown-index-evidence mysql -uroot -pbooktown-evidence

Get-Content -Raw performance/mysql/quiz-history-index/explain.sql |
  docker exec -i booktown-index-evidence mysql -uroot -pbooktown-evidence --table

Get-Content -Raw performance/mysql/quiz-history-index/add-composite-index.sql |
  docker exec -i booktown-index-evidence mysql -uroot -pbooktown-evidence

Get-Content -Raw performance/mysql/quiz-history-index/explain.sql |
  docker exec -i booktown-index-evidence mysql -uroot -pbooktown-evidence --table
```

## 한계

- 로컬 단일 MySQL 컨테이너에서 실행한 쿼리 단위 측정이며 운영 API 응답시간은 아닙니다.
- 데이터 행 크기와 분포를 재현 목적으로 고정했으므로 실제 운영 데이터 분포와 다를 수 있습니다.
- JPA 매핑, 네트워크, JSON 직렬화 시간은 포함하지 않았습니다.
- 따라서 포트폴리오에서는 `EXPLAIN ANALYZE`의 읽은 행과 쿼리 실행시간으로만 표현합니다.
