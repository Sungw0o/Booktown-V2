# [B] Transaction Boundary 재설계 — 외부 I/O 를 트랜잭션 밖으로

> ## ⚠️ 이 문서의 증거 수준을 먼저 밝힌다
>
> | 구분 | 상태 |
> |---|---|
> | 문제 구조 식별 | ✅ **코드·설정으로 확정** — 재현이나 추정이 아니라 소스에 존재하는 사실 |
> | 근본 원인 분석 | ✅ **코드·설정으로 확정** (스레드 수 vs 커넥션 풀 크기 산술) |
> | 코드 변경 | ✅ **적용 완료** + 회귀 방지 테스트 27건 통과 |
> | k6 Before/After 수치 | ❌ **미측정** |
> | Grafana / Loki 증거 | ❌ **미측정** |
>
> 미측정 항목에 추정치를 적지 않았다. 빈 표로 남기고 재현 절차서
> (`perf/RUNBOOK-transaction-boundary.md`)와 전용 k6 스크립트
> (`perf/k6/07-ai-vs-read-contention.js`)를 함께 제공한다.
>
> **면접에서 말할 수 있는 것**: "이 구조가 커넥션 풀 고갈을 유발할 수 있음을 코드로 식별하고
> 트랜잭션 경계를 재설계했다. 다만 부하 상황에서의 개선 폭은 아직 측정하지 못했다."
> **말하면 안 되는 것**: "p95 를 몇 % 개선했다."

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

### 8-1. 일반 조회 API — ❌ 미측정

| 구간 | Before p95 | After p95 | 변화 |
|---|---:|---:|---:|
| AI 없음 (0~1분) | *미측정* | *미측정* | |
| AI 동시 실행 (1~2분) | *미측정* | *미측정* | |
| AI 종료 후 (2~3분) | *미측정* | *미측정* | |
| **AI 부하로 인한 p95 증가 배수** | *미측정* | *미측정* | |

### 8-2. HikariCP — ❌ 미측정

| 지표 | Before | After |
|---|---:|---:|
| `hikaricp_connections_pending` 최대 | *미측정* | *미측정* |
| `hikaricp_connections_active` 평균 (AI 구간) | *미측정* | *미측정* |
| `hikaricp_connections_idle` 최소 | *미측정* | *미측정* |
| 커넥션 획득 p95 | *미측정* | *미측정* |
| `Connection is not available` 로그 건수 | *미측정* | *미측정* |

### 8-3. AI Job — ❌ 미측정

| 지표 | Before | After |
|---|---:|---:|
| Job 처리 시간 p50 / p95 | *미측정* | *미측정* |
| 성공률 | *미측정* | *미측정* |

> **예상되는 결과에 대한 주의**: AI Job 자체의 처리 시간은 개선되지 않는 것이 정상이다.
> 이 변경은 AI 호출을 빠르게 만드는 것이 아니라 AI 호출이 다른 요청의 자원을 뺏지 않게 하는 것이다.
> 트랜잭션이 3개로 나뉘며 커밋 왕복이 늘어 **오히려 소폭 느려질 수도 있다.**
> 측정 후 그렇게 나온다면 그대로 기록해야 한다.

### 8-4. Grafana Metric / Loki 로그 — ❌ 미측정

캡처해야 할 항목은 RUNBOOK 4장에 명시했다.

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

**정직하게 말하면, 아직 부하 상황에서 측정으로 확인하지 못했다.**
현재까지 확인한 것은 **구조적 위험**이며 근거는 코드와 설정이다.

1. 4개 처리기 모두 `@Async + @Transactional` 이 메서드 전체를 감싸고,
   그 안에 OpenAI·Chroma·Gutendex 호출이 있다.
2. 메서드 첫 줄이 `findById` 라 지연 커넥션 획득의 이점이 없다. 즉시 커넥션을 잡는다.
3. AI 워커 최대 스레드 15개 > HikariCP 기본 풀 10개.

이것만으로도 "동시 AI Job 10개면 풀이 고갈된다"는 결론은 산술적으로 나온다.
다만 **실제로 그 지점에 도달하는지**는 부하를 걸어봐야 안다.
그래서 k6 시나리오와 RUNBOOK 을 준비해 두었고, 확인해야 할 지표는
`hikaricp_connections_pending` 이다. 이 값이 0에서 올라가는 순간이 고갈의 직접 증거다.

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
