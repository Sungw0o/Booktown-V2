# Transaction Boundary 개선 — Before/After 부하테스트 실행 절차서

이 문서는 **아직 측정되지 않은** 항목을 직접 채우기 위한 절차서다.
코드 변경(트랜잭션 경계 재설계)은 이미 적용되어 있고, 그 효과를 수치로 증명하는 단계만 남았다.

> 왜 절차서인가
> 트랜잭션 경계 개선 효과는 MySQL·MongoDB·Redis·Chroma·OpenAI 가 모두 붙은 상태에서
> 동시 부하를 걸어야 관측된다. 이 저장소에는 그 실행 결과가 아직 없으므로,
> **추정치를 실측치처럼 적지 않고** 재현 절차만 남긴다.
> 아래 절차대로 실행한 뒤 `perf/results/` 에 raw JSON 을, `docs/` 문서의 표에 수치를 채운다.

---

## 0. 무엇을 증명하려는 것인가

**가설**: 개선 전에는 AI Job 이 동시에 늘어나면 일반 조회 API 의 p95 가 함께 올라간다.
AI 워커 스레드가 외부 API 응답을 기다리는 동안 DB 커넥션을 점유하기 때문이다.

**근거가 되는 설정값** (코드에서 확인 가능, `AsyncConfig` / `application.yml`)

| 항목 | 값 | 출처 |
|---|---:|---|
| HikariCP `maximumPoolSize` | **10** (기본값, 미설정) | `application.yml` 에 `spring.datasource.hikari.*` 없음 |
| HikariCP `connectionTimeout` | 30,000ms (기본값) | 동일 |
| summaryProcessingExecutor maxPoolSize | 4 | `AsyncConfig` |
| quizProcessingExecutor maxPoolSize | 4 | `AsyncConfig` |
| contentProcessingExecutor maxPoolSize | 5 | `AsyncConfig` |
| illustrationProcessingExecutor maxPoolSize | 2 | `AsyncConfig` |
| **AI 워커 스레드 합계** | **15** | 위 4개 합 |

즉 AI 워커가 전부 가동되면 **15개 스레드가 10개짜리 커넥션 풀을 두고 경쟁**한다.
개선 전 구조에서는 워커 1개가 외부 API 응답 대기 내내 커넥션 1개를 붙잡고 있었다.

**증명에 필요한 3종 증거** — 셋이 모두 같은 시간대를 가리켜야 한다.
1. k6: AI 부하 구간에서 조회 API p95 상승
2. Grafana: 같은 시각 `hikaricp_connections_pending` 상승 / `hikaricp_connections_idle` 0
3. Loki: 같은 시각 `HikariPool-1 - Connection is not available, request timed out` 로그

---

## 1. 사전 준비

### 1-1. 관측 지표 노출 확인

`application.yml` 의 actuator 노출에 `prometheus` 가 포함되어 있다(확인 완료).
HikariCP·Executor 지표가 실제로 나오는지 먼저 확인한다.

```bash
curl -s http://localhost:8081/actuator/prometheus | grep -E "hikaricp_connections(_pending|_active|_idle)?\{|executor_(active|queued)"
```

`hikaricp_connections_pending` 이 보이지 않으면 아래 항목이 비어 있는 것이므로 확인한다.
- `spring-boot-starter-actuator`, `micrometer-registry-prometheus` 의존성 (build.gradle 에 이미 있음)
- Prometheus scrape 대상에 `:8081` 이 등록되어 있는지 (`monitoring/prometheus/prometheus.yml`)

### 1-2. 테스트 계정과 시드 데이터

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"perf-test@booktown.local","nickname":"perftest","password":"perf-test-1234"}'
```

도서 시드는 `perf/seed/seed_perf_dataset.sql` 을 쓰되, **AI Job 대상 도서는 `has_content=true`** 여야 한다
(`SummaryService.createSummary` 가 `BOOK_CONTENT_NOT_READY` 로 막는다).
시드 도서는 `has_content` 가 `n % 3 = 0` 인 것만 true 이므로, `BOOK_ID` 는 3의 배수로 지정한다.

### 1-3. 측정 조건 고정 — 반드시 기록할 것

Before/After 를 비교하려면 아래가 **완전히 동일**해야 한다. 실행 시마다 표에 적어 둔다.

| 항목 | 값 |
|---|---|
| 커밋 SHA (before) | |
| 커밋 SHA (after) | |
| 실행 일시 | |
| 머신 사양 (CPU / RAM) | |
| JVM heap (`-Xmx`) | |
| HikariCP maximumPoolSize | |
| Executor core/max/queue | |
| book / summary_job 행 수 | |
| BOOK_ID | |
| READ_VUS / AI_VUS | |
| OpenAI 모델 | |

> ⚠️ OpenAI 응답 시간은 매 실행마다 달라진다. Before/After 를 **같은 날 연속으로** 실행하고,
> `booktown_ai_call_duration_seconds` 의 p50 이 두 실행에서 비슷한지 확인한 뒤에만 비교를 신뢰한다.
> 이 값이 크게 다르면 비교가 성립하지 않으므로 재실행한다.

---

## 2. Before 측정 (개선 전 코드)

```bash
# 개선 직전 커밋으로 이동 (트랜잭션 경계 변경 커밋의 부모)
git stash                       # 또는 별도 워크트리 사용
git checkout <before-commit-sha>

./gradlew clean bootJar
docker compose --profile app up -d --build
# readiness 대기
until curl -sf http://localhost:8080/api/v1/health/readiness > /dev/null; do sleep 3; done
```

k6 실행:

```bash
docker run --rm -i \
  -v "$PWD/perf/k6:/scripts" \
  -v "$PWD/perf/results:/results" \
  -e BASE_URL=http://host.docker.internal:8080/api/v1 \
  -e READ_VUS=20 -e AI_VUS=5 -e BOOK_ID=3 \
  -e MAX_BOOK_ID=5000 -e MAX_PAGE=250 \
  -e RESULT_FILE=/results/07-ai-contention-BEFORE.json \
  grafana/k6 run --summary-trend-stats="avg,min,med,p(90),p(95),p(99),max" \
  /scripts/07-ai-vs-read-contention.js
```

**실행 중** 다음을 캡처한다 (실행이 끝나면 사라진다).

Grafana 대시보드에서 3분 구간을 캡처:
- `hikaricp_connections_active`
- `hikaricp_connections_idle`
- `hikaricp_connections_pending`  ← **핵심 지표**
- `hikaricp_connections_acquire_seconds` (p95)
- `executor_active_threads{name="summaryProcessingExecutor"}`
- `executor_queued_tasks`
- `jvm_memory_used_bytes`, `system_cpu_usage`  ← CPU 병목이 아님을 보이는 데 필요

Prometheus 쿼리로 직접 뽑아 두면 캡처보다 정확하다:

```promql
# 커넥션 획득 대기 중인 스레드 수
hikaricp_connections_pending{pool="HikariPool-1"}

# 커넥션 획득 소요 시간 p95
histogram_quantile(0.95, rate(hikaricp_connections_acquire_seconds_bucket[1m]))

# 조회 API p95 (엔드포인트별)
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{uri=~"/books.*"}[1m])))

# CPU — 병목이 CPU 가 아님을 보이는 근거
system_cpu_usage
```

Loki(또는 컨테이너 로그)에서 커넥션 고갈 로그 확인:

```bash
docker compose logs backend | grep -iE "Connection is not available|timed out|HikariPool"
```

```logql
{container="booktown-backend"} |= "Connection is not available"
```

---

## 3. After 측정 (개선 후 코드)

```bash
git checkout <after-commit-sha>
./gradlew clean bootJar
docker compose --profile app up -d --build
until curl -sf http://localhost:8080/api/v1/health/readiness > /dev/null; do sleep 3; done
```

**2번과 완전히 동일한 명령**으로 k6 실행하되 결과 파일명만 바꾼다.

```bash
docker run --rm -i \
  -v "$PWD/perf/k6:/scripts" \
  -v "$PWD/perf/results:/results" \
  -e BASE_URL=http://host.docker.internal:8080/api/v1 \
  -e READ_VUS=20 -e AI_VUS=5 -e BOOK_ID=3 \
  -e MAX_BOOK_ID=5000 -e MAX_PAGE=250 \
  -e RESULT_FILE=/results/07-ai-contention-AFTER.json \
  grafana/k6 run --summary-trend-stats="avg,min,med,p(90),p(95),p(99),max" \
  /scripts/07-ai-vs-read-contention.js
```

같은 지표를 다시 캡처한다.

---

## 4. 결과 정리 양식

`docs/PERF-B-transaction-boundary.md` 의 표에 아래를 채운다.

### 4-1. 일반 조회 API

| 구간 | Before p95 | After p95 | 변화 |
|---|---:|---:|---:|
| AI 없음 (0~1분) | | | |
| AI 동시 실행 (1~2분) | | | |
| AI 종료 후 (2~3분) | | | |
| **AI 부하로 인한 p95 증가 배수** | | | |

> 가장 중요한 숫자는 마지막 행이다.
> "절대 p95" 는 머신 성능에 좌우되지만 "AI 부하로 인한 증가 배수" 는 구조 개선의 효과를 직접 보여준다.

### 4-2. HikariCP

| 지표 | Before | After |
|---|---:|---:|
| `connections_pending` 최대값 | | |
| `connections_active` 평균 (AI 구간) | | |
| `connections_idle` 최소값 | | |
| 커넥션 획득 p95 | | |
| `Connection is not available` 로그 건수 | | |

### 4-3. AI Job 자체

| 지표 | Before | After |
|---|---:|---:|
| Job 처리 시간 p50 / p95 | | |
| 성공률 | | |
| `booktown_ai_call_duration_seconds` p50 | | |

> AI Job 처리 시간 자체는 **크게 개선되지 않는 것이 정상**이다.
> 이번 변경은 AI 호출을 빠르게 만드는 것이 아니라,
> AI 호출이 다른 요청의 자원을 뺏지 않게 하는 것이기 때문이다.
> 오히려 트랜잭션이 3개로 나뉘며 커밋 왕복이 늘어 소폭 느려질 수 있다. 그렇다면 그대로 기록한다.

### 4-4. 시스템

| 지표 | Before | After |
|---|---:|---:|
| CPU 사용률 (AI 구간 평균) | | |
| Heap 사용량 최대 | | |
| GC pause 합계 | | |

> CPU 가 Before/After 모두 낮게 유지된다면 "병목은 CPU 가 아니라 커넥션 대기였다" 는 근거가 된다.
> 이 표가 없으면 면접에서 "CPU 문제와 어떻게 구분했나" 에 답할 수 없다.

---

## 5. 결과 해석 시 주의사항

1. **효과가 없게 나올 수도 있다.**
   AI_VUS 가 5 이고 요청 간 `sleep(1)` 이 있으면 동시에 살아 있는 AI Job 이 적어
   커넥션 풀(10)을 고갈시키지 못할 수 있다. 이때는 `pending` 이 0에서 안 움직인다.
   그 경우 AI_VUS 를 단계적으로 올려 **고갈이 실제로 재현되는 지점**을 먼저 찾고,
   그 조건에서 Before/After 를 비교한다. 재현되지 않았다면 "재현되지 않았다"고 기록한다.

2. **429 rate limit 에 막히면 AI Job 이 생성되지 않는다.**
   `SummaryService.DAILY_RATE_LIMIT = 10` 이므로 한 계정으로는 하루 10건이 끝이다.
   부하 재현을 위해서는 테스트 계정을 여러 개 만들어 VU 별로 다른 토큰을 쓰거나,
   테스트 프로파일에서만 한도를 올려야 한다. **운영 기본값을 바꾸지 말 것.**

3. **개선 전후로 인덱스 상태가 달라지면 안 된다.**
   이번 작업에는 인덱스 변경도 포함되어 있다. 트랜잭션 경계 효과만 보려면
   Before/After 양쪽 모두 인덱스는 **동일하게 적용된 상태**여야 한다.
   (`V202608120001__perf_indexes.sql` 적용 후 두 코드 버전을 비교)

4. **한 번만 실행하지 말 것.** 최소 3회 반복해 중앙값을 쓴다.
   OpenAI 응답 시간 변동이 커서 1회 측정은 신뢰하기 어렵다.
