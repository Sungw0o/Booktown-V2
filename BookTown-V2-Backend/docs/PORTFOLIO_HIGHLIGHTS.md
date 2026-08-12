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

| 지점 | 현재 코드 | 개선 서사 |
|---|---|---|
| 도서 검색 | `BookRepository`: `title LIKE %:q% OR author LIKE %:q%` 풀스캔 | EXPLAIN 으로 풀스캔 확인 → FULLTEXT 인덱스 → p95 개선 |
| 도서 상세 | `@Cacheable` 없음, 매 요청 DB | Redis 캐시 도입 전/후 비교 |
| AI Job | `SummaryProcessor` 등 `@Async + @Transactional` 결합 | 외부호출 동안 DB 커넥션 점유 → 트랜잭션 경계 분리 |
| 북마크 카운트 | 조회-후-저장 경로 | 원자적 UPDATE / Redis INCR 로 lost update 제거 |
| 비동기 Job | executor 큐 유한(queueCapacity), 재시작 시 인메모리 | 유령 Job 복원력(재처리/영속 큐) |

---

## 우선순위 추천 (포폴 배치)
1. **AI 비용 메트릭 + RAG** → "AI를 운영 가능하게 만든" 서사의 중심
2. **리프레시 토큰 재사용 탐지** → 보안 깊이 어필
3. **배포 자동 롤백 + readiness 격리** → 클라우드/운영 직무 어필
4. 나머지는 "완성도" 근거로 뒤에 배치
