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
