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
