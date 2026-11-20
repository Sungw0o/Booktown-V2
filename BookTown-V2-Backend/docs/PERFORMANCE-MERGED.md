# BookTown V2 — 성능·부하 테스트 문서 모음 (통합본)

> `docs/` 아래 성능/포트폴리오 관련 md 6개를 시간 순서대로 하나로 합친 문서다.
> 원본 파일은 그대로 남아 있으며, 이 문서는 읽기 편의를 위한 병합본이다.
> 각 절 사이의 `---` 구분선과 "**출처**" 표기로 원본 파일 경계를 표시했다.

## 읽는 순서 안내

| # | 원본 파일 | 내용 | 상태 |
|---|---|---|---|
| 1 | `docs/PERF_TEST_CODEX_INSTRUCTIONS.md` | 2026-07-09, 초기 부하테스트 실행 지시서 | 참고용(지시서) |
| 2 | `docs/PERFORMANCE_TEST_REPORT.md` | 2026-07-09, 위 지시서에 따른 1차 실행 결과 (도서 5,000건, 대부분 미완료) | 구버전(참고용) |
| 3 | `docs/PERFORMANCE_PORTFOLIO_SUMMARY.md` | 위 1차 결과의 짧은 요약 | 구버전(참고용) |
| 4 | `docs/PERF-A-index-tuning.md` | 2026-08-12, 실행계획(EXPLAIN ANALYZE) 기반 인덱스 튜닝 — SQL 레벨 실측 완료 | ✅ 최신·완결 |
| 5 | `docs/PERF-B-transaction-boundary.md` | 2026-08-12, AI 트랜잭션 경계 재설계 — k6/Prometheus Before-After 실측 완료 | ✅ 최신·완결 |
| 6 | `docs/PORTFOLIO_HIGHLIGHTS.md` | 성능 외 전체 프로젝트 포트폴리오 하이라이트(보안/CI-CD/관측성 등, 성능은 일부만) | 부록 |

**가장 신뢰할 수 있는 최신 내용은 4번(PERF-A)과 5번(PERF-B)이다.** 1~3번은 도서 5,000건 규모의
초기 측정이라 이후 200,000건 규모로 재설계된 4·5번과 수치가 다르며, 대부분 "미실행/미완료"로
남아 있던 문서다. 나중에 갱신되지 않은 구버전 정보이므로 참고용으로만 남겨둔다.

---

<!-- ============================================================ -->
<!-- 1. PERF_TEST_CODEX_INSTRUCTIONS.md -->
<!-- ============================================================ -->

**출처: `docs/PERF_TEST_CODEX_INSTRUCTIONS.md`**

# [작업 지시서] BookTown 성능·부하 테스트 실행 (Codex 담당)

## 목표
이미 준비된 k6 스크립트로 BookTown 백엔드에 부하/성능/장애 테스트를 **실행**하고,
결과 JSON을 수집해 `docs/PERFORMANCE_TEST_REPORT.md`의 빈칸(`__`)을 실측값으로 채운다.
**스크립트 로직은 새로 짜지 말고 기존 것을 사용**한다. 필요 시 파라미터(env)만 조정.

## 사전 준비물 (이미 저장소에 있음)
- 스크립트: `perf/k6/01~06*.js`, 공용 `perf/k6/lib/config.js`
- 실행 명령 모음: `perf/RUN_COMMANDS.md`  ← **명령은 여기 것을 그대로 복붙**
- 시드 안내: `perf/seed/README.md`, `perf/seed/seed_books.sql`
- 리포트 템플릿: `docs/PERFORMANCE_TEST_REPORT.md`

## 환경 전제
- OS: Windows / 셸: PowerShell / Docker Desktop 실행 중
- 백엔드: 로컬 8080, base path `/api/v1`
- k6: 로컬 설치 금지. `grafana/k6` 도커 이미지로만 실행 (`host.docker.internal:8080` 사용)
- 프로젝트 경로: `C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend`

---

## 실행 순서 (이 순서 그대로)

### STEP 0 — 인프라 기동 & 헬스체크
```powershell
cd C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend
docker compose --profile app --profile monitoring up -d
docker compose ps
curl.exe http://localhost:8080/api/v1/health/readiness   # 200 확인 후 진행
```
`readiness`가 200이 아니면 여기서 멈추고 `docker compose logs backend` 원인부터 보고.

### STEP 1 — 시드 데이터
1. `perf/seed/README.md` 대로 **테스트 전용 계정** 생성 (`perf-test@booktown.local` / `perf-test-1234`).
   - 로그인/회원가입이 Cloudflare Turnstile로 막히면: 로컬 프로파일에서 Turnstile 비활성 확인,
     안 되면 사람에게 보고(임의로 보안설정 변경 금지 — AGENTS.md 규칙).
2. 도서를 충분히 적재(수백~수천 건). `seed_books.sql`의 컬럼은 **실제 `books` 테이블 스키마에 맞춰 수정 후** 실행.
3. 적재 후 실측:
   ```sql
   SELECT COUNT(*) total, MAX(id) max_id FROM books;
   ```
   → 나온 `max_id`, 총 페이지 수를 STEP 2 명령의 `MAX_BOOK_ID`, `MAX_PAGE`에 반영.

### STEP 2 — 조회/검색 baseline (읽기, 안전)
`perf/RUN_COMMANDS.md`의 아래 명령을 순서대로 실행. 각 실행 후 `perf/results/*.json` 생성 확인.
1. `01-books-list-baseline.js`  (GET /books)
2. `02-book-detail-baseline.js` (GET /books/{id})
3. `03-books-search-baseline.js`(GET /books/search)

### STEP 3 — VU 상향 (한도 탐색)
`/books`와 `/books/search`에 대해 VU 10 → 25 → 50 → 100 순으로 상향.
결과 파일명에 VU 표기(예: `01-books-list-vu25.json`). **아래 중단 규칙 엄수.**

### STEP 4 — 쓰기 테스트 (⚠️ DB 기록됨)
1. `04-bookmark-write.js` (POST/DELETE bookmark, 계정 필요)
2. `05-summary-job-write.js` (AI 요약 Job) — **VU 3~5, 30s만.** Gemini 비용/외부 rate limit 주의.
   - 축01 관찰: 05 실행 중 **다른 터미널에서 02를 동시 실행**해 상세 p95 상승폭 기록.

### STEP 5 — 장애 주입 (blast radius)
`perf/RUN_COMMANDS.md`의 06 절차대로:
- 터미널 A: `06-readiness-resilience.js` (2분)
- 터미널 B: 30초 후 `docker compose stop chroma` → 60초 후 `docker compose start chroma`
- 관찰: 장애 구간에 readiness는 503이어도 book detail은 200을 유지하는가.

### STEP 6 — 결과 정리
- `docs/PERFORMANCE_TEST_REPORT.md`의 모든 `__` 를 실측값으로 채움
  (총요청·실패율·평균·p90/p95/p99·threshold 통과·병목후보·개선안).
- Grafana(http://localhost:3000) 에서 부하 구간 캡처 저장:
  요청량/에러율, **HikariCP 커넥션 풀 active/pending**, **async executor 큐 depth**.
- `docs/PERFORMANCE_PORTFOLIO_SUMMARY.md` 의 숫자 자리도 채움.

---

## 🚦 중단 규칙 (반드시 지킬 것)
- **실패율 ≥ 1%**: VU를 더 올리지 말고 즉시 중단 → `docker compose logs --since 5m backend`
  에서 error/timeout/pool/deadlock 확인, DB 상태 점검 후 리포트에 원인 기록.
- **p95 ≥ 1000ms**: 더 올리지 말고 병목 후보를 리포트에 정리.
- **AI(05) 테스트**: 절대 고VU 금지. 외부 API 비용/차단 위험.
- **쓰기 테스트는 로컬 테스트 DB에서만.** 운영 DB 금지.

## ⛔ 하지 말 것 (AGENTS.md 준수)
- `compose.yaml`, `application.yml`, `build.gradle` 임의 수정 금지 (필요하면 사람에게 먼저).
- API 키/시크릿 하드코딩 금지.
- 스키마·보안설정 임의 변경 금지 — 막히면 보고.

## ✅ 최종 산출물 (완료 기준)
1. `perf/results/*.json` — 각 테스트 실행 결과 (VU별 포함)
2. `docs/PERFORMANCE_TEST_REPORT.md` — 실측값으로 전부 채워짐
3. `docs/PERFORMANCE_PORTFOLIO_SUMMARY.md` — 숫자 채워짐
4. Grafana 캡처 이미지 (커넥션 풀·큐 depth 포함)
5. 실행 중 발생한 에러/중단 사유 요약

## 참고: 대상 API (base `/api/v1`)
| 스크립트 | API | 인증 | 노션 축 |
|---|---|---|---|
| 01 | GET /books | X | 조회(03) |
| 02 | GET /books/{id} | X | 캐시(03) |
| 03 | GET /books/search?q= | X | 검색 인덱스(02) |
| 04 | POST/DELETE /books/{id}/bookmark | O | 동시성(04) |
| 05 | POST /books/{id}/summaries | O | 트랜잭션/rate limit(01·05) |
| 06 | GET /health/readiness (+ /books/1) | X | 장애 격리(06) |

---

<!-- ============================================================ -->
<!-- 2. PERFORMANCE_TEST_REPORT.md -->
<!-- ============================================================ -->

**출처: `docs/PERFORMANCE_TEST_REPORT.md`**

# BookTown 성능·부하 테스트 리포트

- 작성일: 2026-07-09
- 대상: BookTown V2 Backend, 로컬 Docker Compose
- 도구: k6 `grafana/k6` Docker image
- API base URL: `http://host.docker.internal:8080/api/v1`
- 공통 threshold: `http_req_failed < 1%`, `http_req_duration p95 < 1000ms`
- 테스트 데이터: `book` 5,000건, `MAX_BOOK_ID=5000`, `MAX_PAGE=250`

> AI 관련 테스트는 사용자 요청으로 생략했다. 추가 Docker 실행은 승인 사용량 제한으로 `/books` 100 VU, `/books/search` VU 상향, bookmark write, Chroma 장애 주입, Grafana 캡처를 완료하지 못했다.

---

## 0. 요약

| 축 | 대상 API | 측정 p95 | 실패율 | threshold | 상태 |
|---|---:|---:|---:|---|---|
| 조회-목록 | GET /books, 10 VU | 40.8ms | 0.00% | PASS | 완료 |
| 조회-목록 | GET /books, 25 VU | 32.7ms | 0.00% | PASS | 완료 |
| 조회-목록 | GET /books, 50 VU | 28.4ms | 0.00% | PASS | 완료 |
| 조회-상세 | GET /books/{id}, 10 VU | 17.2ms | 0.00% | PASS | 완료 |
| 검색 | GET /books/search, 10 VU | 21.7ms | 0.00% | PASS | 완료 |
| 쓰기-북마크 | POST/DELETE /books/{id}/bookmark | 미실행 | 미실행 | 미실행 | 승인 제한 |
| AI Job | POST /books/{id}/summaries | 생략 | 생략 | 생략 | 사용자 요청 |
| 장애 격리 | /health/readiness + 상세 | 미실행 | 미실행 | 미실행 | 승인 제한 |

핵심 결론: 5,000건 데이터 기준 읽기 baseline과 `/books` 50 VU까지는 실패율 0%, p95 29ms 이하로 안정적이었다. 현재 실측 범위에서는 중단 기준인 실패율 1% 또는 p95 1000ms에 도달하지 않았다.

---

## 1. 도서 목록: `GET /books`

- 대상 API: `GET /api/v1/books?page=&size=&sort=&genre=`
- 테스트 데이터 조건: 도서 5,000건, page 0~249 랜덤, size 20
- 스크립트: `perf/k6/01-books-list-baseline.js`

| VU | duration | 결과 파일 | 총 요청 | req/s | 실패율 | 평균 | p90 | p95 | p99 | threshold |
|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---|
| 10 | 1m | `01-books-list-vu10.json` | 1,150 | 19.1 | 0.00% | 23.1ms | 35.7ms | 40.8ms | 50.8ms | PASS |
| 25 | 1m | `01-books-list-vu25.json` | 2,900 | 48.0 | 0.00% | 19.0ms | 26.0ms | 32.7ms | 57.1ms | PASS |
| 50 | 1m | `01-books-list-vu50.json` | 5,801 | 95.9 | 0.00% | 18.0ms | 24.2ms | 28.4ms | 61.4ms | PASS |
| 100 | 1m | `01-books-list-vu100.json` | 미실행 | 미실행 | 미실행 | 미실행 | 미실행 | 미실행 | 미실행 | 승인 제한 |

- 병목 후보: 현재 측정 범위에서는 뚜렷한 병목 없음.
- 개선안: 100 VU 재실행 후 count query, page offset 증가 구간, DB connection pool active/pending을 함께 확인한다.

---

## 2. 도서 상세: `GET /books/{id}`

- 대상 API: `GET /api/v1/books/{bookId}`
- 테스트 데이터 조건: bookId 1~5000 랜덤
- VU / duration: 10 VU / 1m
- 스크립트: `perf/k6/02-book-detail-baseline.js`
- 결과 파일: `perf/results/02-book-detail-vu10.json`

| 지표 | 값 |
|---|---:|
| 총 요청 수 | 1,170 |
| 처리량 | 19.4 req/s |
| 실패율 | 0.00% |
| 평균 응답 | 12.5ms |
| p90 / p95 / p99 | 14.1 / 17.2 / 60.7ms |
| max | 117.9ms |
| threshold 통과 | PASS |

- 병목 후보: 현재 측정 범위에서는 뚜렷한 병목 없음.
- 개선안: 캐시 적용 전후 비교가 필요하면 동일 조건으로 cache variant 결과 파일을 별도 생성한다.

---

## 3. 도서 검색: `GET /books/search?q=`

- 대상 API: `GET /api/v1/books/search?q=&page=&size=`
- 테스트 데이터 조건: 도서 5,000건, 스크립트 내 검색어 세트
- VU / duration: 10 VU / 1m
- 스크립트: `perf/k6/03-books-search-baseline.js`
- 결과 파일: `perf/results/03-books-search-vu10.json`

| 지표 | 값 |
|---|---:|
| 총 요청 수 | 1,170 |
| 처리량 | 19.3 req/s |
| 실패율 | 0.00% |
| 평균 응답 | 15.8ms |
| p90 / p95 / p99 | 19.6 / 21.7 / 29.0ms |
| max | 80.8ms |
| threshold 통과 | PASS |

- 병목 후보: 10 VU 기준에서는 검색 지연이 크지 않았다. 데이터가 증가하면 `%q%` LIKE 검색은 full scan 병목이 될 수 있다.
- 개선안: 25/50/100 VU 상향 재실행과 `EXPLAIN` 확인 후 FULLTEXT 또는 별도 검색 엔진 도입 여부를 판단한다.

---

## 4. 북마크 쓰기: `POST/DELETE /books/{id}/bookmark`

- 대상 API: `POST` / `DELETE /api/v1/books/{bookId}/bookmark`
- 테스트 계정: `perf-test@booktown.local`
- 스크립트: `perf/k6/04-bookmark-write.js`
- 결과 파일: 미생성
- 상태: 추가 Docker 실행 승인 사용량 제한으로 미실행

| 지표 | 값 |
|---|---|
| 총 요청 수 | 미실행 |
| 실패율 | 미실행 |
| 평균 응답 | 미실행 |
| p90 / p95 / p99 | 미실행 |
| threshold 통과 | 미실행 |

- 병목/위험 후보: 동시 북마크 요청 시 unique constraint 충돌, lost update, connection pool 사용량 증가 가능.
- 개선안: 재실행 가능 시 10 VU / 1m로만 실행하고, 요청 후 bookmark count 정합성을 확인한다.

---

## 5. AI Job: `POST /books/{id}/summaries`

- 상태: 사용자 요청으로 생략
- 결과 파일: 미생성

| 지표 | 값 |
|---|---|
| 총 요청 수 | 생략 |
| 2xx/202 | 생략 |
| 429 | 생략 |
| 평균 / p95 | 생략 |

- 비고: Gemini 비용 및 외부 rate limit 리스크가 있어 이번 측정 범위에서 제외했다.

---

## 6. 장애 주입: Readiness Blast Radius

- 스크립트: `perf/k6/06-readiness-resilience.js`
- 결과 파일: 미생성
- 상태: 추가 Docker 실행 승인 사용량 제한으로 미실행

| 구간 | readiness 상태 | book detail 상태 | 판정 |
|---|---|---|---|
| 정상 | 미실행 | 미실행 | 미측정 |
| Chroma 중단 | 미실행 | 미실행 | 미측정 |
| Chroma 복구 | 미실행 | 미실행 | 미측정 |

- 사전 관찰: compose healthcheck는 Chroma 이미지 내부에 `python` 명령이 없어 unhealthy로 표시되지만, 호스트에서 Chroma heartbeat endpoint는 200을 반환했다.
- 개선안: healthcheck 명령을 이미지에 맞게 별도 검토해야 한다. 설정 파일 수정은 금지 범위라 이번 작업에서는 변경하지 않았다.

---

## 7. VU 상향 이력

| 대상 | VU | 총 요청 | 실패율 | p95 | 판정/조치 |
|---|---:|---:|---:|---:|---|
| /books | 10 | 1,150 | 0.00% | 40.8ms | 통과 |
| /books | 25 | 2,900 | 0.00% | 32.7ms | 통과 |
| /books | 50 | 5,801 | 0.00% | 28.4ms | 통과 |
| /books | 100 | 미실행 | 미실행 | 미실행 | 승인 제한 |
| /books/search | 10 | 1,170 | 0.00% | 21.7ms | 통과 |
| /books/search | 25 | 미실행 | 미실행 | 미실행 | 승인 제한 |
| /books/search | 50 | 미실행 | 미실행 | 미실행 | 승인 제한 |
| /books/search | 100 | 미실행 | 미실행 | 미실행 | 승인 제한 |

중단 기준 도달 지점: 측정 완료 범위에서는 없음.

---

## 8. 관측성과 캡처

- Grafana 요청량/에러율 캡처: 미수집, 추가 실행 승인 제한
- Grafana HikariCP active/pending 캡처: 미수집, 추가 실행 승인 제한
- Grafana async executor queue depth 캡처: 미수집, 추가 실행 승인 제한
- backend error log: 미수집, 중단 기준 미도달

---

## 9. 실행 중 특이사항

- 기본 backend 컨테이너는 AI embedding 설정(`Google GenAI project-id`) 누락으로 기동 실패했다.
- 사용자 요청에 따라 AI 관련 테스트를 생략하고, 로컬 임시 backend 컨테이너에 `SPRING_MAIN_LAZY_INITIALIZATION=true`를 주입해 읽기 API만 측정했다.
- Docker Compose 파일, application 설정, build 설정, 보안 설정은 수정하지 않았다.
- 테스트 계정 `perf-test@booktown.local`은 로컬 환경에서 생성했다.

---

<!-- ============================================================ -->
<!-- 3. PERFORMANCE_PORTFOLIO_SUMMARY.md -->
<!-- ============================================================ -->

**출처: `docs/PERFORMANCE_PORTFOLIO_SUMMARY.md`**

# BookTown 성능·부하 테스트 포트폴리오 요약

## 대표 문장

BookTown V2 로컬 백엔드에서 k6 Docker 이미지를 사용해 도서 조회, 상세 조회, 검색 API의 성능을 측정했다. 5,000건의 도서 데이터를 적재한 뒤 `/books`는 10/25/50 VU까지 상향했고, 측정 완료 범위에서 실패율은 모두 0.00%, p95는 최대 40.8ms였다. `/books/{id}` 10 VU는 p95 17.2ms, `/books/search` 10 VU는 p95 21.7ms로 공통 기준인 p95 1000ms 및 실패율 1%를 충분히 만족했다.

AI Job 테스트는 사용자 요청으로 제외했다. 북마크 쓰기, 검색 VU 상향, Chroma 장애 주입, Grafana 캡처는 추가 Docker 실행 승인 사용량 제한으로 미완료 상태이며, 완료된 결과와 미측정 구간을 리포트에 분리해 기록했다.

## 항목별 근거

- **조회 목록**: `/books` 50 VU / 1분에서 5,801 요청, 95.9 req/s, 실패율 0.00%, p95 28.4ms를 기록했다.
- **상세 조회**: `/books/{id}` 10 VU / 1분에서 1,170 요청, 실패율 0.00%, p95 17.2ms를 기록했다.
- **검색**: `/books/search` 10 VU / 1분에서 1,170 요청, 실패율 0.00%, p95 21.7ms를 기록했다.
- **운영 관찰**: AI 설정 누락으로 기본 backend 기동이 실패했으나, 사용자 요청대로 AI 영역을 제외하고 lazy initialization 임시 컨테이너로 읽기 API를 측정했다.
- **남은 검증**: `/books` 100 VU, `/books/search` 25/50/100 VU, 북마크 쓰기, Chroma 장애 주입, Grafana HikariCP/async executor 캡처가 남아 있다.

## 면접 대비 핵심 근거

- 평균 응답시간보다 p95/p99와 실패율을 우선 지표로 삼았다.
- 테스트 데이터 규모와 랜덤 접근 범위를 명시해 재현 가능하게 남겼다.
- 외부 비용이 발생할 수 있는 AI Job은 사용자 지시에 따라 제외했고, 제외 사유를 리포트에 남겼다.
- 인프라 설정 파일은 수정하지 않고 실행 파라미터만 조정했다.

---

<!-- ============================================================ -->
<!-- 4. PERF-A-index-tuning.md -->
<!-- ============================================================ -->

**출처: `docs/PERF-A-index-tuning.md`**

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

---

<!-- ============================================================ -->
<!-- 5. PERF-B-transaction-boundary.md -->
<!-- ============================================================ -->

**출처: `docs/PERF-B-transaction-boundary.md`**

# [B] Transaction Boundary 재설계 — 외부 I/O 를 트랜잭션 밖으로

> ## ⚠️ 이 문서의 증거 수준을 먼저 밝힌다
>
> | 구분 | 상태 |
> |---|---|
> | 문제 구조 식별 | ✅ **코드·설정으로 확정** — 재현이나 추정이 아니라 소스에 존재하는 사실 |
> | 근본 원인 분석 | ✅ **코드·설정으로 확정** (스레드 수 vs 커넥션 풀 크기 산술) |
> | 코드 변경 | ✅ **적용 완료** + 회귀 방지 테스트 27건 통과 |
> | k6 Before/After 수치 | ✅ **실측 (2026-08-12, 3회 반복)** — `perf/results/07-BEFORE-run{1,2,3}.json`, `perf/results/07-AFTER-run{1,2,3}.json` |
> | Prometheus 증거 | ✅ **실측 (2026-08-12, 3회 반복)** — `perf/results/metrics-BEFORE-run{1,2,3}.txt`, `perf/results/metrics-AFTER-run{1,2,3}.txt` |
> | Loki / 커넥션 타임아웃 로그 증거 | ❌ **재현되지 않음** — 아래 8-2, 12장 참조 |
>
> **면접에서 말할 수 있는 것**: "이 구조가 커넥션 풀 고갈을 유발할 수 있음을 코드로 식별하고
> 트랜잭션 경계를 재설계했다. 동일 조건(READ_VUS=20, AI_VUS=15)에서 3회 반복 측정한 결과
> `hikaricp_connections_pending` 최대값이 중앙값 4→1로, `idle=0`(풀 완전 고갈) 지속 빈도가
> 뚜렷이 줄었다. 다만 이번 재현 조건에서는 커넥션 획득 타임아웃(30초) 자체를 넘기는
> 수준까지는 못 갔고, 조회 API p95 증가 배수도 1.19배→1.04배로 완만하게만 개선됐다."
> **말하면 안 되는 것**: "p95 가 몇 배 개선됐다"거나 "커넥션 고갈로 인한 장애를 재현했다" —
> 이번 조건에서는 장애(타임아웃 실패)까지는 재현되지 않았다.

---

## 1. 문제 상황

BookTown 은 요약·퀴즈·삽화·본문수집 4종의 AI/외부 I/O 작업을 `@Async` 로 처리한다.
이 4개 처리기가 모두 아래 형태였다.

```java
@Async("summaryProcessingExecutor")
@Transactional                        // ← 메서드 전체를 감싼다
public void process(Long jobId, boolean isRegeneration) {
    SummaryJob job = summaryJobRepository.findById(jobId).orElseThrow();  // DB 커넥션 획득
    job.markProcessing();
    summaryJobRepository.save(job);

    List<Chapter> chapters = chapterRepository.findAllByBookIdOrderByChapterNumberAsc(bookId);
    String context = buildRagContextIfAvailable(bookId, chapters);   // Chroma HTTP 호출
    String summaryContent = chatClient.prompt()...call().content();  // OpenAI HTTP 호출 ← 수 초~수십 초
    summaryDocumentRepository.save(doc);                             // MongoDB 저장

    job.markCompleted(doc.getId());
    summaryJobRepository.save(job);
}                                     // ← 여기서야 커넥션 반납
```

즉 다음 구조였다.

```
트랜잭션 시작 → DB 커넥션 획득 → Chroma 호출 → OpenAI 호출(수 초~수십 초) → Mongo 저장 → DB 저장 → 커밋
                  └──────────── 이 구간 내내 커넥션 1개 점유, DB 작업은 0건 ────────────┘
```

**중요**: Hibernate 는 지연 커넥션 획득(delayed acquisition)을 하지만, 이 메서드는
**첫 줄이 `findById` 라 곧바로 커넥션을 잡는다.** 지연 획득이 도움이 되지 않는 구조다.

### 4개 처리기 모두 동일했다

| 처리기 | 트랜잭션 안에서 수행하던 외부 I/O |
|---|---|
| `SummaryProcessor` | Chroma 인덱싱 + 검색, OpenAI 호출 최대 2회(메타데이터 번역 + 요약) |
| `QuizProcessor` | OpenAI 호출 1회 |
| `IllustrationProcessor` | 이미지 생성 API 호출 1회 (텍스트보다 느림) |
| `ContentProcessor` | **Gutendex 원문 다운로드(타임아웃 10초)** + OpenAI 번역 |

`ContentProcessor.processFromGutendex` 는 특히 명확하다.
타임아웃 10초짜리 HTTP 다운로드가 트랜잭션 안에 있었다.
최악의 경우 **10초 동안 DB 작업을 하나도 하지 않으면서 커넥션을 붙잡는다.**

### 이미 잘 되어 있던 부분은 건드리지 않았다

`SummaryService.createSummary` 는 이미 `TransactionSynchronization.afterCommit()` 으로
Job 등록 트랜잭션이 커밋된 뒤에 비동기 처리를 시작하고 있었다.
즉 **HTTP 요청 트랜잭션은 이미 짧았다.** 문제는 그 이후 워커 스레드 쪽이었으므로,
이 부분은 그대로 두었다.

---

## 2. 근본 원인 — 스레드 수와 커넥션 풀 크기의 불일치

문제의 심각도는 설정값 산술만으로 확정된다. 추정이 아니다.

### 커넥션 풀 크기

`application.yml`, `application-local.yml`, `application-prod.yml`, `compose.yaml` 전체를
검색한 결과 **`spring.datasource.hikari.*` 설정이 존재하지 않는다.**

따라서 HikariCP 기본값이 적용된다.

| 항목 | 값 |
|---|---:|
| `maximumPoolSize` | **10** |
| `connectionTimeout` | 30,000ms |

### AI 워커 스레드 수 (`AsyncConfig`)

| Executor | core | **max** | queue |
|---|---:|---:|---:|
| `contentProcessingExecutor` | 2 | **5** | 25 |
| `summaryProcessingExecutor` | 2 | **4** | 20 |
| `quizProcessingExecutor` | 2 | **4** | 20 |
| `illustrationProcessingExecutor` | 2 | **2** | 20 |
| **합계** | 8 | **15** | 85 |

### 그래서 무슨 일이 일어나는가

```
AI 워커 최대 스레드 15개  >  커넥션 풀 10개
```

개선 전 구조에서는 **워커 1개 = 커넥션 1개를 외부 API 응답 시간만큼 점유**한다.
따라서

- 동시 AI Job 이 **10개**에 도달하면 커넥션 풀이 완전히 고갈된다.
- 이때 들어온 일반 조회 요청(`GET /books`)은 커넥션을 얻지 못해
  최대 `connectionTimeout` 30초를 기다린 뒤 `SQLTransientConnectionException` 으로 실패한다.
- 큐 용량이 85이므로 최대 100개(15 + 85)의 Job 이 대기하며 이 상태가 지속될 수 있다.

**점유 시간을 더 늘리는 요소**: `application.yml` 의 재시도 설정

```yaml
spring.ai.retry:
  max-attempts: 3
  backoff: { initial-interval: 1s, multiplier: 2, max-interval: 10s }
```

AI 호출이 실패하면 최대 3회 재시도하며 백오프로 최대 10초씩 대기한다.
**재시도 전체가 하나의 트랜잭션 안에서 일어나므로**, 실패 상황에서 커넥션 점유 시간은
정상 응답 때보다 훨씬 길어진다. 외부 API 장애가 곧 DB 커넥션 고갈로 번지는 구조다.

또한 `application*.yml` 에 **OpenAI 호출 타임아웃 설정이 없다.**
(Gutendex 만 코드에 5s/10s 가 하드코딩되어 있다.)
상한이 명시되지 않은 대기 시간 동안 DB 커넥션이 묶인다.

---

## 3. 왜 커넥션 풀을 늘리는 것이 답이 아닌가

가장 먼저 떠오르는 대응은 `maximumPoolSize` 를 20~30으로 올리는 것이다.
이 방법을 선택하지 않은 이유는 다음과 같다.

1. **문제를 뒤로 미룰 뿐이다.** 커넥션이 필요해서 부족한 게 아니라,
   필요 없는 동안 붙잡고 있어서 부족한 것이다. AI 동시 실행 수를 늘리면 다시 고갈된다.
2. **DB 쪽 자원을 낭비한다.** 커넥션은 MySQL 측에서도 스레드와 메모리를 소비한다.
   실제 쿼리를 실행하지 않는 유휴 커넥션을 늘리는 것은 양쪽 모두의 낭비다.
3. **커넥션 풀은 동시성 상한이자 보호 장치이기도 하다.** 무제한으로 늘리면
   DB 가 감당 못 할 동시 쿼리가 유입돼 전체 응답이 함께 느려진다.
4. **AI 처리량을 늘리고 싶다면 Executor 를 늘리는 것이 맞다.**
   트랜잭션 밖으로 외부 I/O 를 빼면 두 값을 **독립적으로** 조절할 수 있게 된다.
   현 구조는 "AI 동시 처리 수를 올리려면 커넥션 풀도 같이 올려야 하는" 결합 상태다.

**결론: 커넥션 점유 시간을 줄이는 것이 근본 해법이고, 풀 크기 조정은 그다음 튜닝이다.**

---

## 4. 변경 내용 — Transaction Boundary 재설계

### 목표 구조

```
[짧은 TX #1]  Job → PROCESSING 저장 + 외부 I/O 에 필요한 데이터를 값으로 추출   → 커넥션 반납
[  TX 밖   ]  Chroma / OpenAI / Gutendex / MongoDB                              → DB 커넥션 점유 0
[짧은 TX #2]  결과 저장 + COMPLETED (또는 FAILED) 전이                          → 커넥션 반납
```

### 구현 방법 — 왜 별도 빈으로 분리했는가

`@Transactional` 은 Spring AOP 프록시로 동작한다.
**같은 클래스 안에서 다른 메서드를 호출하면 프록시를 거치지 않아 트랜잭션이 적용되지 않는다(self-invocation).**

```java
// ❌ 이렇게 하면 markProcessing 에 트랜잭션이 걸리지 않는다
@Component
class SummaryProcessor {
    @Async public void process(Long id) {
        this.markProcessing(id);        // 내부 호출 → 프록시 미경유
    }
    @Transactional void markProcessing(Long id) { ... }
}
```

그래서 트랜잭션 메서드를 **별도 빈**으로 분리하고 주입받아 호출하도록 했다.

| 처리기 (트랜잭션 없음) | 트랜잭션 전용 빈 (신규) |
|---|---|
| `SummaryProcessor` | `SummaryJobTransactionService` |
| `QuizProcessor` | `QuizJobTransactionService` |
| `IllustrationProcessor` | `IllustrationJobTransactionService` |
| `ContentProcessor` | `ContentJobTransactionService` |

`TransactionTemplate` 을 쓰는 방법도 있었지만, 별도 빈 방식을 택한 이유는
**"이 클래스는 DB만, 저 클래스는 외부 I/O만"** 이라는 경계가 코드 구조에 드러나
나중에 실수로 외부 호출을 트랜잭션 안에 다시 넣기 어려워지기 때문이다.

### LazyInitializationException 대응

트랜잭션이 끝나면 영속성 컨텍스트가 닫히므로, 엔티티를 트랜잭션 밖으로 들고 나가
LAZY 프록시에 접근하면 예외가 발생한다.

그래서 1단계 트랜잭션이 **엔티티가 아니라 불변 값 객체(record)** 를 반환하도록 했다.

```java
public record SummaryJobContext(
        Long jobId, Long bookId, Long userId,
        String title, String author, String description,
        List<ChapterContent> chapters
) {}
```

이에 맞춰 함께 변경한 것들
- `RagService.indexChapters(Long, List<Chapter>)` → `List<ChapterContent>` 를 받도록 변경
- `BookVisualPromptFactory.createScenePrompt(...)` — 값 기반 오버로드 추가
  (기존 Scene 기반 시그니처는 유지해 기존 테스트를 깨지 않음)

### 상태 정합성 설계

```
QUEUED ──[TX#1]──> PROCESSING ──[외부 I/O]──> ┬─[TX#2 성공]─> COMPLETED
                                              └─[TX#2 실패]─> FAILED
```

| 상황 | 처리 |
|---|---|
| 1단계 트랜잭션 실패 | Job 을 읽지 못한 것이므로 상태 변경 없이 종료(QUEUED 유지), 에러 로그 |
| 외부 I/O 실패 | `markFailed` 로 FAILED 전이 |
| 결과 저장 실패 | `markFailed` 로 FAILED 전이 |
| FAILED 기록마저 실패 | 예외를 삼키고 `stuck in PROCESSING` 경고 로그 (Executor 스레드 보호) |

**`markFailed` 는 `Propagation.REQUIRES_NEW`** 로 선언했다.
호출 측 트랜잭션이 롤백 표시된 상태여도 FAILED 기록만큼은 반드시 커밋되어야 하기 때문이다.

**퀴즈 저장은 하나의 트랜잭션으로 묶었다.**
`saveQuizAndComplete` 안에서 quiz → question → option 저장과 `markCompleted` 를 함께 처리한다.
문항 일부만 저장된 채 COMPLETED 가 되는 중간 상태를 만들지 않기 위해서다.
`ContentProcessor` 도 동일하게 chapter/scene 저장 + `has_content` 플래그 + COMPLETED 를 한 트랜잭션에 묶었다.

---

## 5. 변경된 파일

**신규 (트랜잭션 경계 전용 빈)**
- `domain/summary/service/SummaryJobTransactionService.java`
- `domain/summary/service/ChapterContent.java`
- `domain/quiz/service/QuizJobTransactionService.java`
- `domain/illustration/service/IllustrationJobTransactionService.java`
- `domain/admin/service/ContentJobTransactionService.java`

**수정 (`@Transactional` 제거 + 위임)**
- `domain/summary/service/SummaryProcessor.java`
- `domain/quiz/service/QuizProcessor.java`
- `domain/illustration/service/IllustrationProcessor.java`
- `domain/admin/service/ContentProcessor.java`
- `domain/summary/service/RagService.java` (값 객체 수용)
- `domain/illustration/service/BookVisualPromptFactory.java` (값 기반 오버로드 추가)

**신규 테스트 (27건, 전부 통과)**
- `SummaryProcessorTransactionBoundaryTest` (9건)
- `QuizProcessorTransactionBoundaryTest` (7건)
- `ContentProcessorTransactionBoundaryTest` (6건)
- `IllustrationProcessorTransactionBoundaryTest` (5건)

---

## 6. 테스트 — 구조가 되돌아가지 않도록

핵심은 **"요약 내용이 맞는가"가 아니라 "AI 호출이 트랜잭션 밖에 있는 구조가 유지되는가"** 다.

**(1) 구조 자체를 검증하는 테스트** — 누군가 다시 `@Transactional` 을 붙이면 즉시 실패한다.

```java
@Test
@DisplayName("SummaryProcessor 에는 @Transactional 이 없어야 한다")
void processor_must_not_be_transactional() {
    assertThat(SummaryProcessor.class.getAnnotation(Transactional.class)).isNull();
    for (Method method : SummaryProcessor.class.getDeclaredMethods()) {
        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }
}
```

**(2) 실행 순서 검증** — 커밋이 외부 호출 앞뒤로 나뉘는지

```java
InOrder order = inOrder(transactionService, gutendexClient);
order.verify(transactionService).startProcessing(JOB_ID);                  // 짧은 TX #1
order.verify(gutendexClient).downloadText(anyString());                    // TX 밖 (HTTP)
order.verify(transactionService).saveContentAndComplete(eq(JOB_ID), ...);  // 짧은 TX #2
```

**(3) 상태 정합성** — 요청받은 케이스를 모두 커버

| 케이스 | 기대 | 커버 |
|---|---|---|
| 정상 완료 | COMPLETED, FAILED 기록 없음 | ✅ |
| 외부 AI 호출 실패 | FAILED, 저장 시도 없음 | ✅ |
| 결과 저장 실패 | FAILED, COMPLETED 아님 | ✅ |
| AI 응답 파싱 실패 | FAILED, `retryable=false` | ✅ |
| 시작 트랜잭션 실패 | AI 호출조차 하지 않음 | ✅ |
| FAILED 기록마저 실패 | 예외 전파 없음 | ✅ |
| 다운로드 실패 | FAILED, 저장 시도 없음 | ✅ |

### 실행 결과

```
전체: 155건, 실패/오류 0건, 스킵 0건  (그중 신규 트랜잭션 경계 테스트 27건)

ContentProcessorTransactionBoundaryTest         tests=6 failures=0
IllustrationProcessorTransactionBoundaryTest    tests=5 failures=0
QuizProcessorTransactionBoundaryTest            tests=7 failures=0
SummaryProcessorTransactionBoundaryTest         tests=9 failures=0
```

기존 128건도 전부 통과했다(변경 전 128건 → 변경 후 155건).

> **커버하지 못한 것**: 중복 요청 방어와 Executor rejection 은
> 이번 변경으로 동작이 바뀌지 않아 별도 테스트를 추가하지 않았다.
> 중복 방어는 `SummaryService.existsByUserIdAndBookIdAndStatusIn` 에 있고,
> Executor rejection 정책도 `AsyncConfig` 그대로다.

---

## 7. 부하테스트 시나리오 (설계 완료 / 실행 미완료)

`perf/k6/07-ai-vs-read-contention.js` 를 새로 작성했다.

**설계 의도**: 조회 baseline 과 AI Job 을 별도 터미널에서 돌리면 시작 시각이 어긋나
"AI 부하 구간의 조회 p95" 를 정확히 잘라낼 수 없다.
그래서 한 실행 안에서 시나리오를 겹치고 구간별 커스텀 메트릭으로 분리했다.

```
0:00 ~ 3:00   read  : 조회 API 지속 호출 (READ_VUS=20)
1:00 ~ 2:00   ai    : 요약 생성 Job 동시 발생 (AI_VUS=5)

→ 0~1분(AI 없음) / 1~2분(AI 있음) / 2~3분(회복) 세 구간의 조회 p95 를 비교
```

전체 실행 절차, 캡처해야 할 Grafana 지표, Prometheus 쿼리, Loki 쿼리,
측정 조건 기록 양식은 **`perf/RUNBOOK-transaction-boundary.md`** 에 정리했다.

---

## 8. Before / After 측정 결과

### 측정 조건 (재현 절차서 3장 형식)

| 항목 | 값 |
|---|---|
| 커밋 SHA (before) | `3009bff` + 부하테스트 인에이블링 커밋(rate-limit `@Value`화, 가짜 ChatClient) 체리픽 |
| 커밋 SHA (after) | `f76ad08`(트랜잭션 경계 재설계) + 위와 동일한 인에이블링 커밋 |
| 실행 일시 | 2026-08-12, 같은 세션에서 BEFORE 3회 → AFTER 3회 연속 실행 |
| HikariCP maximumPoolSize | 10 (기본값, 미설정) |
| summaryProcessingExecutor | core=2, max=4, queue=20 (Before/After 동일, 변경 범위 아님) |
| 인덱스 상태 | `V202608120001__perf_indexes.sql` 목표 상태와 동일 (schema.sql 로 DB 최초 생성 시 이미 반영됨), Before/After 양쪽 동일 DB 컨테이너 재사용 |
| book / summary_job 행 수 | book 200,000 / quiz_submission 2,000,000 / summary_job 1,000,000 (`perf/seed/seed_perf_dataset.sql`) |
| BOOK_ID | 3 (+ VU 별 오프셋, 아래 참조) |
| READ_VUS / AI_VUS | 20 / 15 |
| AI 응답 시간 | 실제 OpenAI 미사용. `PerfFakeAiConfig` 로 고정 지연 8,000ms × 콜당(요약은 메타데이터 번역+본문 생성 2회 호출 발생) |

> **k6 스크립트를 하나 수정했다.** 원본 `07-ai-vs-read-contention.js` 는 모든 AI VU 가 같은
> `BOOK_ID` 하나를 공유해서, `SummaryService` 의 (userId, bookId) 단위 중복 Job 방어
> (`existsByUserIdAndBookIdAndStatusIn`) 때문에 첫 VU 가 만든 Job 이 끝날 때까지 나머지
> VU 는 전부 409 를 받아 **실제 동시 AI Job 수가 AI_VUS 와 무관하게 1개로 고정**되는 버그가
> 있었다. `BOOK_ID + (__VU-1)*3` 으로 VU 마다 다른 책(모두 `has_content=true`)을 요청하도록
> 고쳤다. 이 수정 없이는 Before/After 어느 쪽에서도 커넥션 경합 자체가 재현되지 않는다.

### 8-1. 일반 조회 API — ✅ 실측 (3회, 중앙값 (범위))

| 구간 | Before p95 | After p95 |
|---|---:|---:|
| AI 없음 (0~1분) | 374.3ms (371.3~380.1) | 400.1ms (367.9~402.5) |
| AI 동시 실행 (1~2분) | 449.9ms (397.5~453.1) | 396.2ms (380.9~417.0) |
| AI 종료 후 (2~3분) | 422.5ms (389.8~448.7) | 392.9ms (378.1~460.6) |
| **AI 부하로 인한 p95 증가 배수** | **1.19배 (1.07~1.20)** | **1.04배 (0.95~1.08)** |

> 원본 수치: `perf/results/07-BEFORE-run{1,2,3}.json`, `perf/results/07-AFTER-run{1,2,3}.json`
> (k6 client-side 측정치. RUNBOOK 이 제안한 Prometheus `histogram_quantile` 교차검증은
> 8-2 의 이유로 불가능했다.)
>
> **해석**: 방향성은 가설과 일치한다(Before 1.19배 > After 1.04배 — AI 부하로 인한
> 조회 지연 증가폭이 After 에서 줄었다). 다만 절대적인 개선폭은 크지 않다.
> 이유는 8-2 의 `executor_active_max` 를 보면 알 수 있다: 이번 조건에서는
> `summaryProcessingExecutor` 의 max(4)에도 못 미치는 동시성(관측된 active 최대 2)만
> 나왔고, AI_VUS 를 아무리 올려도 실제 동시 AI 처리 스레드 수는 이 executor 설정에
> 의해 상한이 걸린다. 즉 이번 재현에서 "AI 부하"는 executor 큐 대기까지 포함해 우리가
> 기대했던 만큼 크지 않았다. 이 부분은 **재현이 안 된 것이지 개선 효과가 없는 것이
> 아니다** — 아래 8-2 의 `pending`/`idle` 지표가 더 직접적인 증거다.

### 8-2. HikariCP — ✅ 실측 (3회, 중앙값 (범위))

| 지표 | Before | After |
|---|---:|---:|
| `hikaricp_connections_pending` 최대 | **4 (3~12)** | **1 (0~1)** |
| `hikaricp_connections_active` 최대 | 10 (10~10, 매 실행 max 도달) | 10 (9~10, 1회는 max 미도달) |
| `hikaricp_connections_idle` 최소 | 0 (매 실행 완전 고갈) | 0 (2/3 회 완전 고갈, 1회는 idle=1 유지) |
| 커넥션 획득 대기 최대값(`acquire_seconds_max`) | 0.33~0.44초 | 0.31~0.44초 |
| `Connection is not available` 로그 건수 | **0** | **0** |

> 원본: `perf/results/metrics-BEFORE-run{1,2,3}.txt`, `perf/results/metrics-AFTER-run{1,2,3}.txt`
>
> **가장 직접적인 증거는 `pending` 이다.** Before 는 3회 모두 대기 스레드가 발생했고
> 한 번은 12개까지 쌓였다. After 는 3회 중 2회만, 그것도 최대 1개로 훨씬 짧고 얕게 끝났다.
> `active` 최대값은 Before/After 거의 같은데(둘 다 종종 10=max 도달), 이는 당연하다 —
> 풀 크기 자체(10)는 바꾸지 않았기 때문이다. **바뀐 것은 "그 상태가 얼마나 오래/깊게
> 유지되는가"이고, 그것이 `pending`/`idle` 차이로 나타난다.**
>
> **재현되지 않은 것 — 정직하게 기록한다**: `connectionTimeout`(30초)을 실제로 넘겨
> `SQLTransientConnectionException`/`Connection is not available` 로 요청이 실패하는
> 상황은 Before/After 어느 쪽에서도 만들지 못했다. `pending` 스파이크가 최대 12~18 수준,
> 지속시간이 15~25초 정도로 짧아 30초 타임아웃 안에서 항상 해소됐기 때문이다.
> RUNBOOK 이 요구한 "3종 증거(k6 + Prometheus + Loki)" 중 **Loki/로그 증거는 얻지 못했다.**
> 이 조건에서 실제 장애(요청 실패)까지 재현하려면 `AI_VUS` 를 훨씬 더 올리거나
> `fake-ai-latency-ms` 를 20~30초로 늘려 커넥션 점유 시간 자체를 늘려야 할 것으로 보이나,
> 이번 세션에서는 여기까지 시도하지 못했다.

### 8-3. AI Job — ✅ 실측 (3회) — 참고용, 해석에 주의

| 지표 | Before | After |
|---|---:|---:|
| Job 성공률 (해당 구간 생성된 Job 기준) | 100% (run1 n=8, run2 n=25, run3 n=29 — 전부 COMPLETED) | 100% (run1 n=27, run2 n=27, run3 n=29 — 전부 COMPLETED) |
| Job 처리 시간(생성→완료) 평균 | 82.9s / 56.7s / 47.7s | 47.9s / 53.4s / 47.8s |
| `booktown_ai_call_duration_seconds` p50 | 측정 불가 (아래 참조) | 측정 불가 |

> **`booktown_ai_call_duration_seconds` 는 코드에 정의만 되어 있고 어디서도 호출되지 않는다**
> (`BooktownMetrics.recordAiCall` 를 참조하는 곳이 없음). RUNBOOK 이 제안한 "AI 응답 시간이
> Before/After 에서 비슷했는지" 교차검증은 이 메트릭으로는 불가능했다. 대신 `summary_job`
> 테이블의 `created_at`→`updated_at` 간격(Job 단위 wall-clock, 위 표)으로 대체했는데,
> 이 값은 **AI 호출 시간이 아니라 "executor 큐 대기 + AI 호출 2회(8초×2, 메타데이터 번역이
> 스킵되지 않는 영문 시드 데이터라 항상 2회 호출됨) + DB 저장" 을 합친 값**이라 Before/After
> 를 직접 비교하는 지표로 쓰기엔 부정확하다(특히 Before-run1 은 n=8 로 표본이 작고, 그 구간에
> 만들어진 Job 중 상당수가 다음 구간(run2)에 걸쳐 완료돼 표본이 한쪽으로 치우쳤을 가능성이 있다).
> 8-1/8-2 처럼 방향성 있는 비교가 아니라 **"둘 다 100% 성공했고 수십 초대"라는 정성적 사실만
> 신뢰할 수 있는 숫자로 본다.
>
> 이 결과는 RUNBOOK 이 미리 예고한 대로다: **AI Job 자체의 처리 시간은 개선 대상이 아니다.**
> 이 변경은 AI 호출을 빠르게 만드는 것이 아니라 AI 호출이 다른 요청의 자원을 뺏지 않게
> 하는 것이다.

### 8-4. 시스템(CPU/Heap) — ✅ 실측 (3회, 중앙값)

| 지표 | Before | After |
|---|---:|---:|
| CPU 사용률 평균(측정 구간 전체) | 25.7% | 26.6% |
| CPU 사용률 최대 | 37.5% | 37.1% |
| Heap 사용량 최대 | ~276MB | ~276MB |

> Before/After 모두 CPU 는 40% 를 넘지 않았다. 8-2 의 `pending`/`idle` 고갈과 함께 보면
> **"CPU 는 한가한데 조회가 느려지는" 조합이 Before 에서 더 뚜렷했다** — 병목이 CPU 연산이
> 아니라 커넥션 대기였다는 근거다. Heap 은 두 조건에서 차이가 없었다(예상대로 — 이 변경은
> 메모리 사용 패턴에 영향을 주는 변경이 아니다).
>
> Grafana 대시보드 스크린샷은 캡처하지 않았다 — 위 수치는 전부 Prometheus HTTP API
> (`/api/v1/query_range`)에서 직접 뽑은 원본이며, RUNBOOK 도 "캡처보다 정확하다"고
> 명시하고 있어 이 방식만 사용했다. 원본 쿼리는 `perf/results/collect_metrics.sh` 에 있다.

---

## 9. 트레이드오프

**1. 트랜잭션이 3개로 나뉘어 원자성이 사라졌다**
가장 중요한 변화다. 이전에는 "PROCESSING 저장 → AI → 결과 저장" 전체가 하나의 트랜잭션이라
중간 실패 시 전부 롤백됐다. 이제는 **PROCESSING 이 이미 커밋된 상태**에서 AI 가 실패할 수 있다.

이는 오히려 Job 패턴에 더 맞는 동작이다. Job 테이블의 존재 이유가
"진행 상태를 외부에서 관찰 가능하게" 만드는 것인데, 이전 구조에서는
처리 중에 PROCESSING 커밋이 안 되어 있어 **조회 API 가 PROCESSING 상태를 볼 수 없었다.**
(같은 트랜잭션 안에서만 보이는 미커밋 상태였다.)
변경 후에야 폴링 API 가 실제 진행 상태를 볼 수 있다.

대신 **PROCESSING 에서 멈춘 Job(stuck job)이 생길 수 있다.**
FAILED 기록 트랜잭션마저 실패한 경우다. 로그로는 남지만 자동 복구는 없다.
→ **후속 과제**: 일정 시간 이상 PROCESSING 인 Job 을 FAILED 로 정리하는 스케줄러 필요.

**2. DB 왕복이 늘었다**
1회 트랜잭션 → 2~3회 트랜잭션이므로 커밋 왕복과 엔티티 재조회가 늘었다.
각 트랜잭션이 밀리초 단위임을 감안하면 수 초 걸리는 AI 호출 대비 무시할 수준이지만,
**AI Job 개별 처리 시간은 소폭 늘어날 수 있다.**

**3. 코드가 늘었다**
클래스 4개, 값 객체 record 다수가 추가됐다. 엔티티를 그대로 넘기던 것에 비해 장황하다.
다만 이 장황함이 "트랜잭션 경계가 어디인지"를 코드에 드러내는 값이라고 판단했다.

**4. 여전히 해결되지 않은 것**
- HikariCP 설정이 명시적으로 없어 기본값 10에 의존한다. → 명시적 설정 권장
- OpenAI 호출 타임아웃이 설정되어 있지 않다. → 상한 설정 필요
- `RagService.indexChapters` 가 요약 생성 시마다 같은 책을 재인덱싱한다.
  이번 변경 범위가 아니어서 손대지 않았지만 중복 벡터가 쌓이는 문제가 있다.

---

## 10. 면접 예상 질문

### Q1. DB 가 병목이라고 어떻게 판단했는가?

**구조적 위험을 코드/설정으로 먼저 확인한 뒤, 부하테스트로 실측까지 했다.**

구조적 근거:
1. 4개 처리기 모두 `@Async + @Transactional` 이 메서드 전체를 감싸고,
   그 안에 OpenAI·Chroma·Gutendex 호출이 있다. (개선 전 코드 기준)
2. 메서드 첫 줄이 `findById` 라 지연 커넥션 획득의 이점이 없다. 즉시 커넥션을 잡는다.
3. AI 워커 최대 스레드 15개 > HikariCP 기본 풀 10개.

**실측 결과 (2026-08-12, READ_VUS=20 / AI_VUS=15, 3회 반복, 자세한 수치는 8장)**:
- `hikaricp_connections_pending` 이 개선 전 코드에서 3회 모두 0보다 크게 올라갔다
  (중앙값 4, 최대 12). 개선 후 코드에서는 3회 중 2회만, 그것도 최대 1까지만 올라갔다.
- `hikaricp_connections_idle` 은 개선 전 3회 모두 0(완전 고갈)까지 떨어졌고,
  개선 후에는 3회 중 1회는 고갈되지 않았다(idle=1 유지).
- 조회 API p95 는 AI 부하 구간에서 개선 전 1.19배, 개선 후 1.04배 상승했다(중앙값).

**정직하게 밝혀야 할 한계**: 이번 조건(AI_VUS=15, 고정 지연 8초)에서는
`connectionTimeout`(30초)을 실제로 넘겨 요청이 실패하는 수준의 "완전한 장애"까지는
재현하지 못했다 — `pending` 스파이크가 15~25초 정도로 짧아 타임아웃 전에 항상 해소됐다.
`summaryProcessingExecutor` 의 `maxPoolSize=4` 때문에 `AI_VUS` 를 올려도 실제 동시 AI
처리 스레드는 그 이상 늘지 않는다는 것도 이번 재현에서 확인했다 — 즉 이번 조건은
"풀이 완전히 무너지는" 최악의 시나리오가 아니라 "풀이 자주 포화되고 대기가 발생하는"
중간 강도의 부하였다. 그럼에도 **같은 조건에서 Before 대비 After 가 `pending`/`idle`
양쪽에서 일관되게 덜 심각했다는 방향성**은 3회 반복 모두에서 재현됐다.

### Q2. CPU 문제와 어떻게 구분했는가?

세 지표를 같은 시간대에 겹쳐 보면 구분된다.

| | 커넥션 풀 고갈 | CPU 병목 |
|---|---|---|
| `system_cpu_usage` | 낮게 유지 | 100%에 근접 |
| `hikaricp_connections_pending` | 0보다 크게 상승 | 0 근처 |
| `hikaricp_connections_active` | max(10)에 고정 | 변동 |
| 스레드 상태 | 대부분 WAITING | 대부분 RUNNABLE |

핵심은 **"CPU 는 놀고 있는데 요청은 느리다"** 는 조합이다.
AI 호출은 네트워크 대기(I/O bound)라 CPU 를 거의 쓰지 않는다.
따라서 CPU 가 낮은데 조회 p95 가 오르고 `pending` 이 함께 오른다면 커넥션 대기가 원인이다.

이 구분이 RUNBOOK 에 CPU/heap/GC 표를 넣어 둔 이유다. 이 표가 없으면 이 질문에 답할 수 없다.

### Q3. Connection Pool 고갈을 어떻게 확인했는가?

세 가지가 같은 시각을 가리켜야 한다.

1. **메트릭**: `hikaricp_connections_pending > 0`, `hikaricp_connections_idle == 0`,
   `hikaricp_connections_active == 10`(=max)
2. **로그**: `HikariPool-1 - Connection is not available, request timed out after 30000ms`
3. **애플리케이션 증상**: 같은 시각 조회 API p95 상승 또는 500 에러

메트릭만으로는 "잠깐 튄 것"과 구분이 어렵고, 로그만으로는 규모를 알 수 없다.
`pending` 이 **핵심 지표**인 이유는 active 는 정상 부하에서도 max 에 닿을 수 있지만
pending 은 "이미 기다리는 스레드가 있다"는 뜻이라 포화를 직접 의미하기 때문이다.

### Q4. `@Transactional` 은 언제 Connection 을 획득하는가?

Hibernate 기본은 **지연 획득(delayed acquisition)** 이다.
`@Transactional` 진입 시점이 아니라 **첫 JDBC 작업이 실제로 필요한 시점**에 획득하고,
트랜잭션이 끝날 때까지 반납하지 않는다.

그런데 이 코드에서는 그 이점이 없었다. `process()` 의 첫 줄이

```java
SummaryJob job = summaryJobRepository.findById(jobId).orElseThrow();
```

이라 메서드 진입 직후 커넥션을 잡고, 그 뒤 OpenAI 호출까지 계속 들고 있었다.

**"지연 획득이 있으니 괜찮다"는 흔한 오해**를 실제 코드로 반박할 수 있는 지점이다.
지연 획득은 "DB 를 안 쓰는 트랜잭션"에서만 도움이 되고,
"DB 를 먼저 쓰고 그다음 외부 호출"인 구조에서는 아무 도움이 되지 않는다.

### Q5. 외부 API 호출을 Transaction 안에 두면 왜 위험한가?

1. **커넥션 점유 시간이 외부 서비스에 종속된다.** 응답 시간을 우리가 통제할 수 없는데
   그 시간만큼 우리 자원이 묶인다. 외부 장애가 곧 우리 DB 커넥션 고갈이 된다.
2. **재시도가 점유를 배가한다.** 이 프로젝트는 `max-attempts: 3`, 백오프 최대 10초다.
   재시도 전체가 한 트랜잭션 안이라 실패 시 점유 시간이 훨씬 길어진다.
3. **락 유지 시간이 길어진다.** 트랜잭션 중 갱신한 행의 락이 외부 호출 내내 유지되어
   다른 트랜잭션의 대기와 데드락 위험을 키운다.
4. **롤백이 외부 효과를 되돌리지 못한다.** OpenAI 호출은 이미 과금됐고 되돌릴 수 없다.
   DB 만 롤백되면 "돈은 썼는데 기록이 없는" 불일치가 생긴다.
5. **트랜잭션 타임아웃과 충돌한다.** DB 나 커넥션 풀 타임아웃이 외부 응답보다 짧으면
   응답이 도착해도 커밋할 수 없다.

### Q6. HikariPool 크기를 늘리는 것으로 해결하면 안 되는가?

안 되는 이유는 위 3장에 정리했다. 요약하면

- 커넥션이 부족한 게 아니라 **필요 없는 동안 붙잡고 있는 것**이 문제다.
- 풀을 늘려도 AI 동시 실행 수를 늘리면 다시 고갈된다. 상한만 미뤄진다.
- 유휴 커넥션은 MySQL 측 스레드·메모리도 소비한다.
- 커넥션 풀은 DB 보호를 위한 동시성 상한이기도 하다.

다만 **트랜잭션 경계를 고친 다음에는** 풀 크기를 명시적으로 설정하는 것이 맞다고 본다.
현재는 기본값 10에 암묵적으로 의존하고 있어 의도가 코드에 없다.

### Q7. `@Async` Thread 수와 Connection Pool 크기의 관계는?

**개선 전에는 강하게 결합되어 있었다.**
워커 1개가 작업 내내 커넥션 1개를 점유하므로 사실상

```
필요한 커넥션 수 ≥ 동시 실행 AI 워커 수
```

이 프로젝트는 워커 15 > 풀 10 이라 이 조건이 깨져 있었다.

**개선 후에는 결합이 끊어진다.** 워커는 DB 작업을 하는 짧은 순간에만 커넥션을 쓰므로,
필요한 커넥션 수는 워커 수가 아니라 **"단위 시간당 DB 작업 횟수 × 작업 시간"** 으로 결정된다.
AI 호출이 10초, DB 작업이 10ms 라면 점유율은 0.1% 수준이라
워커 15개가 커넥션 1~2개를 나눠 쓰는 것으로도 충분해진다.

**실무적 의미**: 이제 AI 처리량을 늘리려고 Executor 를 키울 때 커넥션 풀을 같이 키우지 않아도 된다.
두 값을 각자의 이유로 조절할 수 있게 된 것이 이 변경의 진짜 이득이다.

### Q8. Transaction 을 쪼개면 원자성은 어떻게 보장하는가?

**전체 원자성은 포기했고, 대신 두 가지로 대응했다.**

**(1) 꼭 함께 커밋돼야 하는 것만 한 트랜잭션으로 묶었다.**
`saveQuizAndComplete` 는 quiz + question + option 저장과 `markCompleted` 를 한 트랜잭션에 넣는다.
문항이 일부만 저장된 채 COMPLETED 가 되는 상태를 막기 위해서다.
`ContentProcessor` 도 chapter/scene 저장 + `has_content` + COMPLETED 를 함께 묶었다.

**(2) 나머지는 상태 머신으로 정합성을 관리한다.**

```
QUEUED → PROCESSING → COMPLETED
                   └→ FAILED
```

전체 롤백 대신 "어디까지 진행됐는지"를 상태로 기록한다.
이것이 원래 Job 테이블을 둔 이유이기도 하다.

**남은 위험**: FAILED 기록마저 실패하면 PROCESSING 에 갇힌다.
그래서 `markFailed` 를 `REQUIRES_NEW` 로 선언해 호출 측 롤백과 무관하게 커밋되도록 했고,
그마저 실패하면 `stuck in PROCESSING` 경고 로그를 남긴다.
**완전한 해결책은 타임아웃 기반 정리 스케줄러이며, 이는 후속 과제로 남겼다.**

### Q9. AI 호출 성공 후 DB 저장 실패 시 어떻게 처리하는가?

**현재 동작**: FAILED 로 전이한다. 전용 테스트로 검증했다
(`marks_failed_when_result_persistence_fails`).

**여기서 정직하게 인정할 점**: 이 경우 **AI 비용은 이미 지출됐는데 결과는 버려진다.**
사용자는 재요청해야 하고 비용이 두 번 나간다.

이는 트랜잭션을 쪼갠 결과가 아니라 **원래부터 있던 문제**다.
개선 전에도 DB 저장 실패 시 롤백됐고 AI 호출은 되돌릴 수 없었다.

더 나은 처리 방향은 이렇게 정리했다.

1. **결과를 먼저 안전한 곳에 저장한다.** 현 구조에서 요약 본문은 MongoDB 에 먼저 저장되고
   그다음 MySQL 의 Job 상태가 갱신된다. 즉 MySQL 저장이 실패해도 **요약 본문 자체는 남아 있다.**
   따라서 재시도 시 AI 를 다시 부르지 않고 기존 문서를 연결하는 복구가 가능하다.
2. **저장 실패는 재시도 대상으로 분류한다.** 현재 `retryable` 판정은 예외 메시지 문자열
   매칭에 의존해 정교하지 않다. 저장 실패는 명확히 재시도 가능으로 분류하는 것이 맞다.
3. **멱등성 키를 둔다.** 같은 jobId 로 재처리할 때 중복 문서가 쌓이지 않도록
   `summaryJobId` 기준 upsert 가 필요하다.

이 세 가지는 이번 변경 범위를 넘어서므로 적용하지 않았고, 후속 과제로 명시한다.

---

<!-- ============================================================ -->
<!-- 6. PORTFOLIO_HIGHLIGHTS.md -->
<!-- ============================================================ -->

**출처: `docs/PORTFOLIO_HIGHLIGHTS.md`**

# BookTown V2 — 포트폴리오에 적을 만한 것들 (코드 근거 기반)

> 실제 저장소를 훑어 "면접에서 말하면 먹히는" 지점만 추림. 각 항목에 **코드 근거**와 **포폴/면접 문장**을 붙였다.
> 스택 기준: Java 21, Spring Boot 4.0.6, Spring AI, MySQL·MongoDB·Redis·ChromaDB, Prometheus/Grafana/Loki, GitHub Actions.

---

## 🥇 S급 (희소성 높음 — 신입 포폴에서 잘 안 보임)

### 1. 리프레시 토큰 재사용 탐지 + 원자적 회전 (Redis Lua)
- **근거**: `RefreshTokenService.rotate()` 가 Redis Lua 스크립트로 `GET → 비교 → 불일치 시 DEL(전체 무효화) → 일치 시 교체`를 **원자적으로** 수행. `ErrorCode.REFRESH_TOKEN_REUSED` 존재.
- **왜 강한가**: 탈취된 토큰이 재사용되면 세션 전체를 revoke 하는 보안 업계 표준(refresh token rotation with reuse detection)을 직접 구현. check-then-act race 를 Lua 로 제거한 것도 포인트.
- **문장**: "리프레시 토큰을 Redis에 저장하고 회전(rotation)할 때, Lua 스크립트로 기존 토큰 검증·교체를 원자적으로 처리했습니다. 저장된 값과 다른 토큰이 오면 재사용(탈취)으로 간주해 해당 사용자의 전체 세션을 무효화합니다."

### 2. AI 호출 비용·토큰·지연 커스텀 메트릭
- **근거**: `BooktownMetrics` — `booktown_ai_call_duration_seconds`(Timer), `booktown_ai_calls_total`(status 태그), `booktown_ai_tokens`(DistributionSummary), `booktown_ai_cost_usd`. Prometheus 알림 `BookTownAiFailureOrRateLimit` 로 실패/rate limit 감지.
- **왜 강한가**: "AI 붙였다"에서 멈추지 않고 **운영 관점(비용·토큰·실패율)을 지표화**. LLM 서비스의 실제 리스크(비용 폭증, rate limit)를 관측 대상으로 만든 점.
- **문장**: "Gemini 호출을 provider/model/operation/status 태그로 계측해 지연·토큰·추정비용·실패율을 Prometheus로 수집하고, AI 실패나 rate limit 이 발생하면 알림이 뜨도록 구성했습니다."
- **⚠️ 갱신 필요**: `docs/PERF-B-transaction-boundary.md` 8-3 절 실측 결과 `booktown_ai_call_duration_seconds` 는 **정의만 되어 있고 실제 호출부(`SummaryProcessor`, `QuizProcessor` 등)에서 한 번도 호출되지 않는다**(`recordAiCall` 참조처가 없음). 위 문장은 "계측 코드를 만들었다"까지는 맞지만 "실제로 수집되고 있다"는 실측으로 반증됐다. 포폴에 쓰려면 이 갭을 먼저 메우거나, 문장에서 "계측 인프라를 준비했다"로 톤을 낮춰야 한다.

### 3. 배포 실패 자동 롤백 (readiness 게이트)
- **근거**: `cicd.yml` CD job — 새 이미지 up 후 `https://api.booktown.shop/.../health/readiness` 를 12회×10초 폴링, 실패 시 **직전 이미지 태그로 backend 자동 롤백**(배포 전 `PREVIOUS_IMAGE` 캡처).
- **왜 강한가**: 무중단 배포의 핵심인 "건강 확인 후 확정, 실패 시 복구"를 스크립트로 실제 구현. 롤백 대상 이미지를 미리 잡아두는 디테일.
- **문장**: "배포 후 readiness 를 폴링해 통과해야만 배포를 확정하고, 실패하면 배포 전에 기록해둔 직전 이미지로 자동 롤백하도록 CD 파이프라인을 구성했습니다."

---

## 🥈 A급 (탄탄하고 설명하기 좋음)

### 4. 멀티 데이터스토어 통합 readiness (blast radius 관찰 가능)
- **근거**: `HealthController.checkReadiness()` 가 MySQL·Redis·MongoDB·Chroma 4개를 각각 점검(Chroma 는 3초 타임아웃 RestClient), 하나라도 죽으면 503.
- **활용**: 노션 계획의 "Chroma 장애 전파" 테스트와 직결. (현재는 Chroma 죽으면 readiness 503 → 개선 여지: 부가 의존성 강등. 이건 **개선 서사**로 쓰기 좋음.)
- **문장**: "readiness 에서 핵심/부가 저장소를 구분 점검하도록 하고, 부가 의존성(Chroma) 장애가 핵심 API로 전파되지 않게 격리하는 실험을 진행했습니다."

### 5. 도메인별 전용 스레드풀 분리
- **근거**: `AsyncConfig` — content/summary/quiz/illustration 4개 executor 를 각각 core/max/queue 다르게 설정, `@Async("...Executor")` 로 지정.
- **왜 강한가**: AI 작업 종류별로 자원을 격리해 한 작업 폭주가 다른 작업을 굶기지 않게 함(bulkhead 패턴).
- **문장**: "요약·퀴즈·삽화·원문처리를 별도 executor 로 분리해, 특정 AI 작업의 폭주가 다른 파이프라인을 막지 않도록 격리했습니다."
- **참고**: `docs/PERF-B-transaction-boundary.md` 실측 결과 `summaryProcessingExecutor` (core=2, max=4) 가 병렬 AI 처리량의 실질적 상한임을 확인했다. AI_VUS 를 15까지 올려도 관측된 동시 활성 스레드는 최대 2였다 — bulkhead 로서는 기능하지만, "처리량"을 늘리려면 이 executor 크기 조정이 먼저 필요하다는 후속 과제가 실측으로 뒷받침됐다.

### 6. RAG 파이프라인 (Spring AI + Chroma, book 단위 필터)
- **근거**: `RagService.indexChapters()` 로 챕터를 벡터 저장, `retrieveContext()` 는 `similaritySearch(topK + bookId filterExpression)` 로 **해당 도서 범위 내에서만** 검색. `SummaryProcessor` 가 이 컨텍스트로 요약 생성.
- **문장**: "도서 원문을 챕터 단위로 임베딩해 ChromaDB에 저장하고, 요약 생성 시 bookId로 필터링한 유사도 검색으로 관련 원문만 컨텍스트로 주입하는 RAG 구조를 구현했습니다."

### 7. 관측성 3종: TraceId + 구조화 로그 + 민감정보 마스킹
- **근거**: `TraceIdFilter`(X-Trace-Id 검증/발급, MDC, 요청 완료 시 method/uri/status/duration 로그), `StructuredJsonLogLayout`(JSON 로그), `SensitiveDataMasker`(password/token/secret/authorization/cookie 정규식 마스킹) → Loki 수집.
- **문장**: "모든 요청에 trace id 를 부여해 MDC·응답헤더로 전파하고, 구조화 JSON 로그에서 비밀번호·토큰·쿠키를 정규식으로 마스킹한 뒤 Loki로 수집했습니다."

---

## 🥉 B급 (기본기·완성도 어필)

### 8. 공급망 보안까지 챙긴 CI/CD
- **근거**: `cicd.yml` — 액션을 **커밋 SHA로 핀 고정**, 워크플로우 기본 `permissions: contents:read`(CD만 packages:write 승격), `persist-credentials:false`, concurrency 취소, SonarCloud + Jacoco 커버리지, GHCR + buildx gha 캐시, dependabot automerge.
- **문장**: "GitHub Actions 를 최소 권한·SHA 핀 고정으로 구성하고, 테스트·정적분석(Sonar)·커버리지(Jacoco)를 게이트로 두었습니다."

### 9. 프로덕션 Dockerfile 하드닝
- **근거**: 멀티스테이지 빌드(builder/runtime 분리), **non-root 유저**(booktown) 실행, `MaxRAMPercentage=75` 컨테이너 메모리 인지, 의존성 레이어 선캐싱.
- **문장**: "멀티스테이지 + non-root + 컨테이너 메모리 인지 JVM 옵션으로 운영 이미지를 최적화했습니다."

### 10. 일관된 API 규약 & 예외 설계
- **근거**: 모든 컨트롤러가 `*Api` 인터페이스 구현(문서/시그니처 분리), `ApiResponse`/`PageMeta` 응답 봉투 통일, `ErrorCode` enum 38종 + `GlobalExceptionHandler`(검증 에러 필드 매핑, trace id 포함, 인증 실패는 메트릭 기록).
- **문장**: "응답 봉투와 에러 코드를 표준화하고, 컨트롤러를 API 인터페이스로 분리해 스펙과 구현을 나눴습니다."

### 11. 운영 알림 룰 (도메인 특화)
- **근거**: `booktown-alerts.yml` — 5xx 비율, 인증 실패 급증(booktown_auth_failures_total), AI 실패/rate limit, 디스크 15%, 컨테이너 메모리 90%, **MySQL 백업 정체(backup stale)** 까지.
- **문장**: "일반 지표뿐 아니라 인증 실패 급증·AI 실패·백업 정체 같은 도메인 특화 알림까지 정의했습니다."

---

## 🔧 "개선 서사"로 쓸 병목 (성능 테스트와 세트) — 약점을 강점으로

이건 자랑거리라기보다, **부하 테스트 → 발견 → 개선**의 스토리를 만들 재료. (노션 계획과 직결)

| 지점 | 현재 코드 | 개선 서사 | 진행 상태 |
|---|---|---|---|
| 도서 검색 | `BookRepository`: `title LIKE %:q% OR author LIKE %:q%` 풀스캔 | EXPLAIN 으로 풀스캔 확인 → FULLTEXT 인덱스 → p95 개선 | ✅ 측정 완료(`PERF-A`) — FULLTEXT 는 정합성 문제로 **적용하지 않기로 결정**, 근거를 실측으로 남김 |
| 도서 상세 | `@Cacheable` 없음, 매 요청 DB | Redis 캐시 도입 전/후 비교 | 미착수 |
| AI Job | `SummaryProcessor` 등 `@Async + @Transactional` 결합 | 외부호출 동안 DB 커넥션 점유 → 트랜잭션 경계 분리 | ✅ 코드 변경 + 실측 완료(`PERF-B`) — `pending` 중앙값 4→1, 다만 커넥션 타임아웃 수준의 장애까지는 재현하지 못함 |
| 북마크 카운트 | 조회-후-저장 경로 | 원자적 UPDATE / Redis INCR 로 lost update 제거 | 미착수 |
| 비동기 Job | executor 큐 유한(queueCapacity), 재시작 시 인메모리 | 유령 Job 복원력(재처리/영속 큐) | 미착수 |

---

## 우선순위 추천 (포폴 배치)
1. **AI 비용 메트릭 + RAG** → "AI를 운영 가능하게 만든" 서사의 중심 (단, 위 2번 항목의 갱신 필요 참고)
2. **리프레시 토큰 재사용 탐지** → 보안 깊이 어필
3. **배포 자동 롤백 + readiness 격리** → 클라우드/운영 직무 어필
4. **인덱스 튜닝(PERF-A) + 트랜잭션 경계(PERF-B)** → 실측 기반 성능 개선 서사, 개선되지 않은 부분까지 정직하게 기록한 점이 차별화 포인트
5. 나머지는 "완성도" 근거로 뒤에 배치
