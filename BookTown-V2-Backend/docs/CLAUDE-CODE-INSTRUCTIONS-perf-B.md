# Claude Code 작업 지시서 — [B] 트랜잭션 경계 개선 효과 실측

> 이 문서를 Claude Code 세션에 그대로 붙여넣거나, `@docs/CLAUDE-CODE-INSTRUCTIONS-perf-B.md` 로 참조시킨다.

---

## 0. 배경 — 이미 끝난 일 / 남은 일

**이미 완료됐다. 다시 하지 마라.**

| 항목 | 상태 | 산출물 |
|---|---|---|
| 프로젝트 전수 분석 | ✅ | — |
| 인덱스 튜닝 (EXPLAIN ANALYZE Before/After 실측) | ✅ | `docs/PERF-A-index-tuning.md`, `perf/results/explain/` |
| 인덱스 마이그레이션 | ✅ | `src/main/resources/db/migration/V202608120001__perf_indexes.sql` |
| 시드 스크립트 | ✅ | `perf/seed/seed_perf_dataset.sql` |
| 트랜잭션 경계 재설계 (코드 변경) | ✅ | Processor 4종 + TransactionService 4종 |
| 회귀 방지 테스트 27건 | ✅ | `*TransactionBoundaryTest.java` (총 155건 통과) |
| 부하테스트 시나리오 설계 | ✅ | `perf/k6/07-ai-vs-read-contention.js` |
| 실행 절차서 | ✅ | `perf/RUNBOOK-transaction-boundary.md` |

**남은 일 = 이 지시서의 전부**

`docs/PERF-B-transaction-boundary.md` 8장의 표가 전부 *미측정* 으로 비어 있다.
그 표를 **실측값으로 채우는 것**이 목표다.

---

## 1. 검증하려는 가설

개선 전 코드는 `@Async + @Transactional` 이 처리기 메서드 전체를 감싸고 있어서,
AI/외부 API 응답을 기다리는 동안 DB 커넥션을 계속 점유했다.

```
AI 워커 최대 스레드 15개  >  HikariCP 기본 풀 10개
```

따라서 **동시 AI Job 이 10개에 도달하면 커넥션 풀이 고갈되어
일반 조회 API(`GET /books`)까지 느려지거나 실패한다**는 것이 가설이다.

개선 후에는 외부 I/O 가 트랜잭션 밖으로 나갔으므로 이 현상이 사라져야 한다.

**증명에 필요한 3종 증거 — 셋이 같은 시각을 가리켜야 한다**

1. k6: AI 부하 구간에서 조회 API p95 상승
2. Prometheus/Grafana: 같은 시각 `hikaricp_connections_pending` 상승
3. Loki/로그: 같은 시각 `Connection is not available, request timed out`

---

## 2. 환경 — 사용자가 Docker Compose 를 켜 둔 상태

인프라는 **이미 다 구성되어 있다. 새로 만들지 마라.**

```bash
cd BookTown-V2-Backend
./scripts/local-infra.sh monitor     # 앱 + MySQL/Redis/Mongo/Chroma + Prometheus/Grafana/Loki
./scripts/local-infra.sh health      # 기동 확인
```

| 서비스 | URL |
|---|---|
| 앱 API | http://localhost:8080/api/v1 |
| Actuator (메트릭) | http://localhost:8081/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 (admin / `.env` 의 `GRAFANA_ADMIN_PASSWORD`) |
| Loki | http://localhost:3100 |

Prometheus 는 이미 `backend:8081/actuator/prometheus` 를 스크랩하도록 설정돼 있다.

---

## 3. 반드시 지킬 원칙

1. **측정하지 않은 값을 문서에 쓰지 마라.** 추정치·예상치 금지.
2. **효과가 없으면 없다고 써라.** 개선이 안 나오면 원인을 분석해서 적어라.
3. **재현이 안 되면 "재현되지 않았다"고 써라.** 조건을 바꿔가며 억지로 만들지 마라.
4. **기존 기능을 훼손하지 마라.** 특히 운영 기본값(rate limit, 풀 크기)을 영구 변경하지 마라.
5. **한 번에 하나씩 검증하라.** 단계마다 결과를 보고하고 다음으로 넘어가라.
6. 작업 전 **현재 상태와 계획을 먼저 보고**하라.

---

## 4. STEP 1 — 사전 정리 (코드 변경 전)

### 4-1. 현재 변경분을 커밋한다

Before/After 비교를 하려면 "개선 전 코드"로 체크아웃할 수 있어야 한다.
지금은 변경분이 워킹 트리에만 있다.

```bash
git switch -c perf/transaction-boundary
git add -A
git commit -m "perf: 실행계획 기반 인덱스 튜닝 및 AI 처리기 트랜잭션 경계 재설계"
git rev-parse HEAD          # ← AFTER 커밋 SHA. 기록해 둘 것
git rev-parse HEAD~1        # ← BEFORE 커밋 SHA. 기록해 둘 것
```

> ⚠️ `git status` 에 전체 파일이 수정된 것처럼 보이면 **CRLF/LF 차이 때문**이다.
> 실제 변경은 20개 파일뿐이다. `git diff --ignore-all-space --stat` 으로 확인하라.
> 만약 전체 파일이 통째로 커밋되려 하면 먼저 아래를 실행하고 다시 시도하라.
> ```bash
> git config core.autocrlf true
> git add --renormalize .
> ```

### 4-2. 인덱스는 Before/After 양쪽 모두 동일해야 한다

이번 작업에는 인덱스 변경도 포함돼 있다.
**트랜잭션 경계 효과만 보려면 인덱스는 양쪽 다 적용된 상태여야 한다.**

BEFORE 커밋으로 돌아가면 `schema.sql` 도 옛 버전이 되므로,
DB 는 **재생성하지 말고 유지**하고 마이그레이션만 한 번 적용해 둔다.

```bash
docker compose -p booktown-backend exec -T mysql \
  mysql -uroot -pbooktown-root booktown < src/main/resources/db/migration/V202608120001__perf_indexes.sql
```

적용 후 확인:

```sql
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='booktown'
  AND TABLE_NAME IN ('quiz_submission','summary_job','illustration_job')
GROUP BY 1,2 ORDER BY 1,2;
```

> `ddl-auto: update` 인 local 프로파일에서 BEFORE 코드로 앱을 띄우면
> 엔티티의 옛 `@Index` 정의 때문에 `idx_quiz_submission_user_id` 가 다시 생길 수 있다.
> 생기더라도 **삭제하지 말고 그대로 두라.** 양쪽 조건을 같게 유지하는 것이 우선이고,
> 이 인덱스 유무는 AI 커넥션 경합과 무관하다. 다만 **생겼다는 사실은 기록**하라.

**보고**: 커밋 SHA 2개, 인덱스 목록 출력. 여기서 멈추고 사용자 확인을 받아라.

---

## 5. STEP 2 — 부하 재현을 막는 2가지 장애물 해결

그냥 k6 를 돌리면 **커넥션 풀이 고갈되지 않아 아무것도 관측되지 않는다.**
이유가 두 가지 있고, 둘 다 해결해야 한다.

### 5-1. 장애물 A: Rate limit 이 AI Job 생성을 막는다

`SummaryService.DAILY_RATE_LIMIT = 10` 이다.
계정 하나로는 **10건 쏘고 나머지는 전부 429** 라서 동시 AI Job 이 10개에 도달하지 못한다.

**해결: perf 프로파일에서만 한도를 설정으로 뺀다. 운영 기본값은 10 그대로 유지한다.**

`SummaryService` 의 상수를 `@Value` 주입으로 바꾼다.

```java
// 기존: private static final int DAILY_RATE_LIMIT = 10;
private final int dailyRateLimit;

public SummaryService(..., @Value("${booktown.summary.daily-rate-limit:10}") int dailyRateLimit) {
    ...
    this.dailyRateLimit = dailyRateLimit;
}
```

기본값이 10 이므로 **설정을 안 주면 동작이 완전히 동일하다.**
`application-perf.yml` 에서만 크게 잡는다.

> ⚠️ 이 변경은 BEFORE/AFTER **양쪽 커밋에 모두 들어가야 한다.**
> BEFORE 브랜치에 cherry-pick 하거나, 이 변경을 두 커밋의 공통 조상에 넣어라.
> 한쪽에만 있으면 비교가 무효다.

### 5-2. 장애물 B: 실제 OpenAI 호출은 비싸고 비교를 무효화한다

OpenAI 응답 시간은 매 실행마다 5~30초로 요동친다.
Before 실행이 8초, After 실행이 20초였다면 **개선 효과인지 응답시간 차이인지 구분할 수 없다.**
게다가 반복 실행마다 과금된다.

**해결: perf 프로파일 전용 가짜 ChatClient 를 만들어 지연 시간을 고정한다.**

커넥션 풀 고갈은 "외부 호출이 오래 걸린다"는 사실 하나로 발생한다.
그게 OpenAI 든 `Thread.sleep` 이든 **DB 커넥션 입장에서는 완전히 동일하다.**
오히려 독립변수를 통제하므로 실험으로서 더 엄밀하다.

`src/main/java/com/booktown/global/config/PerfFakeAiConfig.java` (신규):

```java
@Profile("perf")
@Configuration
public class PerfFakeAiConfig {

    /**
     * 부하 테스트 전용 가짜 ChatClient.
     * 커넥션 풀 경합을 재현하려면 "외부 호출이 오래 걸린다"는 조건만 있으면 되고,
     * 실제 OpenAI 호출은 응답 시간이 요동쳐 Before/After 비교를 무효화한다.
     * 따라서 지연 시간을 고정해 독립변수를 통제한다.
     */
    @Bean
    @Primary
    public ChatClient fakeChatClient(
            @Value("${booktown.perf.fake-ai-latency-ms:8000}") long latencyMs) {
        // ChatClient 인터페이스를 구현하거나, Spring AI 의 ChatModel 을
        // 고정 지연 + 고정 응답으로 스텁한 뒤 ChatClient.create(model) 로 감싼다.
        // 요약용 응답: 700자 이상 한국어 텍스트
        // 퀴즈용 응답: 유효한 JSON 배열
    }
}
```

**구현 시 주의**
- `SummaryProcessor` 는 `ObjectProvider<ChatClient>` 로 받으므로 `@Primary` 빈이면 주입된다.
- 실제 Spring AI 2.0.0-M6 의 `ChatClient` 인터페이스 시그니처를 먼저 확인하고 구현하라.
  `ChatModel` 을 스텁해서 `ChatClient.create(chatModel)` 로 만드는 편이 보통 더 쉽다.
- 응답 내용이 `QuizProcessor.extractJson()` 을 통과해야 한다(대괄호 JSON 배열).
- **`@Profile("perf")` 를 반드시 붙여라.** local/prod 에 새어 나가면 안 된다.

### 5-3. `application-perf.yml` (신규)

```yaml
# 부하 테스트 전용 프로파일. 운영/로컬 동작에 영향을 주지 않는다.
spring:
  config:
    activate:
      on-profile: perf

booktown:
  summary:
    daily-rate-limit: ${PERF_DAILY_RATE_LIMIT:100000}   # rate limit 사실상 해제
  perf:
    fake-ai-latency-ms: ${PERF_FAKE_AI_LATENCY_MS:8000} # AI 응답 8초로 고정

logging:
  level:
    com.zaxxer.hikari: DEBUG    # 커넥션 풀 상태 로그
```

**보고**: 변경 파일 목록과 diff. 사용자 확인 후 진행.

---

## 6. STEP 3 — 고갈이 실제로 재현되는 지점부터 찾는다

**Before/After 를 바로 비교하지 마라.** 먼저 **BEFORE 코드에서 고갈이 재현되는지** 확인해야 한다.
재현이 안 되는 조건에서 비교하면 "차이 없음"이라는 무의미한 결과만 나온다.

```bash
git checkout <BEFORE-SHA>
# 5-1, 5-2 변경을 cherry-pick 또는 적용
SPRING_PROFILES_ACTIVE=local,perf ./gradlew clean bootJar
docker compose -p booktown-backend --profile app --profile monitoring up -d --build
```

`AI_VUS` 를 **3 → 5 → 8 → 12 → 20** 으로 올려가며 각각 짧게 돌리고,
매번 아래 Prometheus 쿼리로 `pending` 을 확인한다.

```promql
max_over_time(hikaricp_connections_pending{pool="HikariPool-1"}[3m])
```

```bash
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "$PWD/perf/k6:/scripts" -v "$PWD/perf/results:/results" \
  -e BASE_URL=http://host.docker.internal:8080/api/v1 \
  -e READ_VUS=20 -e AI_VUS=8 -e BOOK_ID=3 \
  -e MAX_BOOK_ID=5000 -e MAX_PAGE=250 \
  -e RESULT_FILE=/results/probe-ai8.json \
  grafana/k6 run --summary-trend-stats="avg,min,med,p(90),p(95),p(99),max" \
  /scripts/07-ai-vs-read-contention.js
```

**판정 기준**
- `pending` 이 0에서 올라가고 조회 p95 가 상승 → **재현 성공.** 그 `AI_VUS` 값을 고정한다.
- `AI_VUS=20` 까지 올려도 `pending` 이 0 → **재현 실패.** 억지로 만들지 말고,
  `fake-ai-latency-ms` 를 20000 으로 올려 다시 시도. 그래도 안 되면
  **"재현되지 않았다"고 보고**하고 원인(Executor 큐잉으로 동시 실행이 제한됐을 가능성 등)을 분석하라.

**보고**: 각 `AI_VUS` 별 `pending` 최대값 표, 고정할 조건. 사용자 확인 후 진행.

---

## 7. STEP 4 — BEFORE 측정 (3회 반복)

STEP 3 에서 고정한 조건으로 **동일 명령을 3회** 실행한다. 1회 측정은 신뢰하지 않는다.

```bash
for i in 1 2 3; do
  docker run --rm -i --add-host=host.docker.internal:host-gateway \
    -v "$PWD/perf/k6:/scripts" -v "$PWD/perf/results:/results" \
    -e BASE_URL=http://host.docker.internal:8080/api/v1 \
    -e READ_VUS=20 -e AI_VUS=<고정값> -e BOOK_ID=3 \
    -e MAX_BOOK_ID=5000 -e MAX_PAGE=250 \
    -e RESULT_FILE=/results/07-BEFORE-run$i.json \
    grafana/k6 run --summary-trend-stats="avg,min,med,p(90),p(95),p(99),max" \
    /scripts/07-ai-vs-read-contention.js
  sleep 60
done
```

### 실행 중/직후 반드시 수집할 것

**Prometheus (curl 로 직접 뽑아 파일 저장 — 스크린샷보다 정확하다)**

```bash
Q='hikaricp_connections_pending{pool="HikariPool-1"}'
curl -sG http://localhost:9090/api/v1/query_range \
  --data-urlencode "query=$Q" \
  --data-urlencode "start=$(date -d '-5 min' +%s)" \
  --data-urlencode "end=$(date +%s)" \
  --data-urlencode "step=5" > perf/results/metrics-BEFORE-pending.json
```

같은 방식으로 아래도 저장한다.

| 지표 | PromQL |
|---|---|
| 커넥션 대기 | `hikaricp_connections_pending{pool="HikariPool-1"}` |
| 활성 커넥션 | `hikaricp_connections_active{pool="HikariPool-1"}` |
| 유휴 커넥션 | `hikaricp_connections_idle{pool="HikariPool-1"}` |
| 획득 시간 p95 | `histogram_quantile(0.95, rate(hikaricp_connections_acquire_seconds_bucket[1m]))` |
| 조회 API p95 | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{uri=~"/books.*"}[1m])))` |
| CPU | `system_cpu_usage` |
| Heap | `jvm_memory_used_bytes{area="heap"}` |
| Executor 활성 | `executor_active_threads` |
| Executor 큐 | `executor_queued_tasks` |

> **CPU 지표는 반드시 받아라.** "병목이 CPU 가 아니라 커넥션 대기였다"를
> 증명하는 유일한 근거이고, 면접 단골 질문이다.

**로그**

```bash
docker compose -p booktown-backend logs backend \
  | grep -iE "Connection is not available|timed out|HikariPool" \
  > perf/results/log-BEFORE-hikari.txt
wc -l perf/results/log-BEFORE-hikari.txt
```

**Grafana 캡처**: http://localhost:3001 에서 해당 3분 구간 대시보드 스크린샷을
`docs/images/perf-B-before-*.png` 로 저장.

---

## 8. STEP 5 — AFTER 측정 (완전히 동일한 조건, 3회)

```bash
git checkout <AFTER-SHA>
# 5-1, 5-2 변경이 적용돼 있는지 확인
SPRING_PROFILES_ACTIVE=local,perf ./gradlew clean bootJar
docker compose -p booktown-backend --profile app --profile monitoring up -d --build
```

**STEP 4 와 토씨 하나 다르지 않게** 실행하되 파일명만 `AFTER` 로 바꾼다.
`READ_VUS`, `AI_VUS`, `BOOK_ID`, `fake-ai-latency-ms` 전부 동일해야 한다.

### 비교 유효성 검증 (중요)

두 실행의 AI 응답 시간이 비슷했는지 먼저 확인하라. 가짜 ChatClient 를 썼다면
고정값이므로 당연히 같아야 한다. 다르면 설정이 잘못된 것이니 재실행하라.

```promql
histogram_quantile(0.5, rate(booktown_ai_call_duration_seconds_bucket[5m]))
```

---

## 9. STEP 6 — 문서 채우기

`docs/PERF-B-transaction-boundary.md` **8장의 *미측정* 표를 실측값으로 교체**한다.
문서의 나머지 구조·서술은 이미 완성돼 있으니 **표만 채워라.**

### 8-1. 일반 조회 API

| 구간 | Before p95 | After p95 | 변화 |
|---|---:|---:|---:|
| AI 없음 (0~1분) | | | |
| AI 동시 실행 (1~2분) | | | |
| AI 종료 후 (2~3분) | | | |
| **AI 부하로 인한 p95 증가 배수** | | | |

> **가장 중요한 숫자는 마지막 행이다.**
> 절대 p95 는 머신 성능에 좌우되지만, "AI 부하로 인한 증가 배수"는 구조 개선 효과를 직접 보여준다.
> 3회 실행이므로 **중앙값과 범위를 함께** 적어라 (예: `4.2배 (3.8~4.6)`).

### 8-2 ~ 8-4

RUNBOOK 4장의 양식대로 HikariCP / AI Job / 시스템 표를 채운다.

### 문서 상단 증거 수준 표도 갱신하라

```
| k6 Before/After 수치 | ❌ 미측정 |   ← 이 줄들을
| Grafana / Loki 증거  | ❌ 미측정 |
```
를 `✅ 실측 (YYYY-MM-DD, 3회 반복)` 로 바꾸고, 원본 파일 경로를 명시하라.

### 그리고 문서 맨 아래 "10. 면접 예상 질문" 의 Q1 을 갱신하라

현재 Q1 답변은 *"아직 부하 상황에서 측정으로 확인하지 못했다"* 로 되어 있다.
측정이 끝났으면 **실측 근거로 다시 써라.** 측정했는데 옛 문장이 남아 있으면 모순이다.

---

## 10. STEP 7 — 회귀 검증 및 정리

```bash
./gradlew test          # 155건 전부 통과해야 한다
git diff --ignore-all-space --stat
```

**되돌릴 것**
- `application-perf.yml` 과 `PerfFakeAiConfig` 는 **남겨도 된다** (perf 프로파일 전용이라 안전).
  단, README 나 문서에 "부하 테스트 전용"임을 명시하라.
- rate limit 의 `@Value` 기본값이 **10 인지 반드시 확인**하라. 이게 바뀌면 운영 동작이 바뀐다.
- STEP 4-2 에서 되살아난 인덱스가 있다면 최종 상태를 마이그레이션과 일치시켜라.

---

## 11. 최종 보고 양식

1. 재현된 고갈 조건 (`AI_VUS`, 지연 설정, `pending` 최대값)
2. Before/After 조회 API p95 표 (3회 중앙값 + 범위)
3. Before/After HikariCP 지표 표
4. CPU/Heap/GC 표 — **CPU 가 낮게 유지됐는지**
5. `Connection is not available` 로그 건수 Before/After
6. 개선 배수 한 줄 요약
7. **재현되지 않았거나 개선이 없었던 항목** — 있으면 반드시 명시
8. 변경 파일 목록
9. 테스트 결과 (155건 기준)
10. 이 결과로 **말해도 되는 주장 / 말하면 안 되는 주장** 정리

---

## 12. 예상되는 함정 (미리 알고 있어라)

| 함정 | 대응 |
|---|---|
| `host.docker.internal` 이 Linux 에서 안 됨 | `--add-host=host.docker.internal:host-gateway` 추가 (위 명령에 이미 포함) |
| 시드 도서의 `has_content` 가 false 라 요약 API 가 400 반환 | 시드는 `n % 3 = 0` 만 true. `BOOK_ID` 를 3의 배수로 지정 |
| BEFORE 코드로 앱이 안 뜸 (`ddl-auto: validate` 충돌) | local 프로파일은 `update` 이므로 정상. prod 로 띄우지 마라 |
| Grafana 대시보드에 HikariCP 패널이 없음 | `hikaricp_connections_pending` 으로 패널을 직접 추가하거나 Prometheus UI 에서 직접 조회 |
| 개선 효과가 없게 나옴 | **그대로 보고하라.** Executor 큐잉 때문에 실제 동시 실행이 10 미만이었을 가능성을 먼저 확인 |
| AI Job 이 전부 FAILED | 가짜 ChatClient 응답이 파싱을 통과 못 한 것. 요약은 텍스트, 퀴즈는 JSON 배열이어야 함 |

---

## 13. 하지 말아야 할 것

- ❌ 수치를 추정해서 채우기
- ❌ 개선이 잘 나온 1회 실행만 골라 쓰기
- ❌ 운영 기본값(rate limit 10, 풀 크기) 영구 변경
- ❌ `@Profile("perf")` 없이 가짜 ChatClient 등록
- ❌ 인덱스를 Before/After 한쪽에만 적용
- ❌ 이미 완료된 [A] 인덱스 작업 다시 하기
- ❌ 한 번에 전부 실행하고 마지막에 몰아서 보고하기 (단계마다 보고할 것)
