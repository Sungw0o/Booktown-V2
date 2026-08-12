# BookTown k6 부하 테스트 — 실행 명령 모음 (PowerShell)

> 전제: Docker Desktop 실행 중 / BookTown 백엔드가 로컬 8080 포트로 기동됨.
> k6 는 로컬 설치 없이 `grafana/k6` 도커 이미지로 실행한다.
> 컨테이너 안에서 host 의 8080 에 접근하려면 `host.docker.internal` 을 쓴다.

프로젝트 경로: `C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend`

---

## 0. 인프라 기동

```powershell
cd C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend

# app(backend) + monitoring(prometheus/grafana/loki 등) 프로파일 기동
docker compose --profile app --profile monitoring up -d

# 상태 확인
docker compose ps
curl.exe http://localhost:8080/api/v1/health/readiness
```

## 1. 결과 폴더 확인

```powershell
# 없으면 생성 (git 에는 .gitkeep 로 이미 존재)
New-Item -ItemType Directory -Force -Path .\perf\results | Out-Null
```

---

## 공통 실행 형태

경로에 공백/한글이 없으면 아래 한 줄 형태를 그대로 쓴다. PowerShell 줄바꿈은 백틱(`` ` ``).
`--summary-trend-stats` 로 p99 까지 산출하고, 스크립트의 `handleSummary` 가 `/results` 에 요약 JSON 을 저장한다.

```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8080/api/v1 `
  -e K6_VUS=10 -e K6_DURATION=1m `
  -e RESULT_FILE=/results/01-books-list-vu10.json `
  -e TARGET_LABEL="GET /books (list)" `
  -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\k6:/scripts `
  -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\results:/results `
  grafana/k6 run `
  --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" `
  /scripts/01-books-list-baseline.js
```

---

## 2. 우선순위별 실행

### [1] 핵심 조회 — 도서 목록 `GET /books`

```powershell
docker run --rm -e BASE_URL=http://host.docker.internal:8080/api/v1 -e K6_VUS=10 -e K6_DURATION=1m -e MAX_PAGE=10 -e RESULT_FILE=/results/01-books-list-vu10.json -e TARGET_LABEL="GET /books" -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\k6:/scripts -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\results:/results grafana/k6 run --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" /scripts/01-books-list-baseline.js
```

### [1] 핵심 조회 — 도서 상세 `GET /books/{id}` (캐시 전/후 비교 대상)

```powershell
docker run --rm -e BASE_URL=http://host.docker.internal:8080/api/v1 -e K6_VUS=10 -e K6_DURATION=1m -e MAX_BOOK_ID=100 -e RESULT_FILE=/results/02-book-detail-vu10.json -e TARGET_LABEL="GET /books/{id}" -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\k6:/scripts -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\results:/results grafana/k6 run --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" /scripts/02-book-detail-baseline.js
```

### [2] 검색 `GET /books/search?q=` (LIKE 풀스캔 → FULLTEXT 인덱스 전/후 비교)

```powershell
docker run --rm -e BASE_URL=http://host.docker.internal:8080/api/v1 -e K6_VUS=10 -e K6_DURATION=1m -e RESULT_FILE=/results/03-books-search-vu10.json -e TARGET_LABEL="GET /books/search" -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\k6:/scripts -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\results:/results grafana/k6 run --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" /scripts/03-books-search-baseline.js
```

### [3] 쓰기 — 북마크 `POST/DELETE /books/{id}/bookmark` (⚠️ DB 쓰기 / 인증 필요)

먼저 `perf/seed/README.md` 대로 테스트 계정을 만든다. 그다음:

```powershell
docker run --rm -e BASE_URL=http://host.docker.internal:8080/api/v1 -e K6_VUS=10 -e K6_DURATION=1m -e MAX_BOOK_ID=100 -e TEST_EMAIL=perf-test@booktown.local -e TEST_PASSWORD=perf-test-1234 -e RESULT_FILE=/results/04-bookmark-write-vu10.json -e TARGET_LABEL="POST/DEL bookmark" -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\k6:/scripts -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\results:/results grafana/k6 run --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" /scripts/04-bookmark-write.js
```

### [3 / AI] 요약 Job `POST /books/{id}/summaries` (⚠️⚠️ Gemini 호출·비용. 낮은 VU 만)

```powershell
docker run --rm -e BASE_URL=http://host.docker.internal:8080/api/v1 -e K6_VUS=3 -e K6_DURATION=30s -e BOOK_ID=1 -e TEST_EMAIL=perf-test@booktown.local -e TEST_PASSWORD=perf-test-1234 -e RESULT_FILE=/results/05-summary-job-vu3.json -e TARGET_LABEL="POST summaries (AI)" -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\k6:/scripts -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\results:/results grafana/k6 run --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" /scripts/05-summary-job-write.js
```

> 축01(트랜잭션 분리) 관찰: 위 05 를 돌리는 **동안** 다른 PowerShell 창에서 02(도서 상세)를 동시에 실행하고,
> AI Job 폭주 시 상세 조회 p95 가 얼마나 치솟는지 비교한다(개선 전 = 커넥션 풀 점유로 지연 예상).

### [6] 장애 시나리오 — Readiness blast radius

```powershell
# 터미널 A: 프로브 시작 (2분)
docker run --rm -e BASE_URL=http://host.docker.internal:8080/api/v1 -e K6_VUS=5 -e K6_DURATION=2m -e RESULT_FILE=/results/06-readiness-resilience.json -e TARGET_LABEL="readiness+detail during fault" -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\k6:/scripts -v C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\perf\results:/results grafana/k6 run --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" /scripts/06-readiness-resilience.js
```

```powershell
# 터미널 B: 30초쯤 뒤 Chroma 강제 종료 → 1분 뒤 복구
docker compose stop chroma
Start-Sleep -Seconds 60
docker compose start chroma
```

관찰 포인트: 장애 구간에 `readiness` 는 503 이 되어도 `book detail` 은 200 을 유지하는가(= 장애 격리 성공).

---

## 3. VU 상향 절차 (한도 탐색)

1. VU 10 → 25 → 50 → 100 순으로 올린다.
2. **실패율 ≥ 1% 이면 즉시 중단**하고 백엔드 로그(`docker compose logs -f backend`)와 DB 상태부터 확인.
3. **p95 ≥ 1000ms 이면** 더 올리지 말고 병목 후보를 리포트에 기록(리포트 템플릿 참고).
4. 각 단계 결과 파일명에 VU 를 표기: `01-books-list-vu25.json` 처럼.

## 4. 결과 확인

```powershell
Get-Content .\perf\results\01-books-list-vu10.json
docker compose logs --since 5m backend | Select-String -Pattern "error|timeout|pool|deadlock"
```

Grafana: http://localhost:3000 (compose 의 grafana 포트 확인) — 부하 구간의 DB 커넥션 풀 사용률·executor 큐 depth 캡처.
