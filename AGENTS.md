# BookTown V2 — 루트 하네스 (Root Harness)

> 이 파일은 **Antigravity, Claude Code, Codex** 세 에이전트가 BookTown V2 전체 스택에서 작업하기 전에 반드시 읽어야 하는 최상위 하네스다.
> 하위 하네스 파일(`BookTown-V2-Backend/skills.md`, `BookTown-V2-Frontend/AGENTS.md`)보다 이 파일이 상위 기준이다.
> 구버전 문서나 대화 기록과 충돌하면 이 파일, Notion `리팩토링` 페이지, `컨벤션 최종`, `API 명세 최종`, `README 최종`, `WBS v5 최종`을 우선한다.

---

## 0. 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 서비스명 | BookTown (책고을) |
| 설명 | AI 기반 고전문학 인터랙티브 독서 플랫폼 |
| 구조 | 모노레포 (`BookTown-V2-Backend/`, `BookTown-V2-Frontend/`) |
| 백엔드 | Java 21, Spring Boot 4.0, Gradle, 포트 8080 |
| 프론트엔드 | React 19 + TypeScript + Vite + Tailwind CSS 4 |
| API Base URL | `/api/v1` / 프로덕션: `https://api.booktown.shop` |
| GitHub 기본 브랜치 | `dev` (배포: `main`) |

---

## 1. 하네스 작업 흐름

모든 에이전트는 코드 작성 전에 아래 순서를 따른다.

```
AGENTS.md (이 파일)
  → 담당 하위 하네스 파일 확인
      백엔드: BookTown-V2-Backend/skills.md
      프론트: BookTown-V2-Frontend/AGENTS.md
  → Notion WBS v5 우선순위 확인
  → Notion API 명세 최종 계약 확인
  → 허용된 코드 영역에서 구현
  → 테스트 · lint · build 통과
  → 사람 검토 (PR)
  → GitHub Issue 체크박스 갱신
```

---

## 2. 에이전트 역할 및 협업 모델

세 에이전트는 **풀스택을 유기적으로 순환**하며 작업한다. 역할은 **주 강점 기준**이며 상황에 따라 유연하게 전환된다.

### 에이전트별 주 강점

| 에이전트 | 주 강점 영역 | 보조 영역 |
|---|---|---|
| **Antigravity** | 프론트엔드 구현 (React, Tailwind, 브라우저 UI 검증) | API 연동, 타입 정의 |
| **Claude Code** | 백엔드 복잡한 분석·설계·멀티파일 리팩토링, 인증·AI·Job 구조 | 프론트엔드 복잡 로직 분석 |
| **Codex** | 백엔드 명세 기반 구현 (CRUD, 테스트, PR 수정), API 정합성 검증 | 체크리스트·문서 갱신 |

### 협업 규칙

- **한 브랜치 + 한 PR = 한 AI가 주 담당**이다.
- 다른 AI는 같은 브랜치에서 직접 수정하지 않고 **리뷰, 검증, 문서화, 체크리스트 갱신**을 담당한다.
- 주 담당 AI가 막히는 경우에만 다른 AI로 전환하고, 전환 이유를 PR 또는 Issue에 기록한다.
- `.env`, Secret, EC2 접속, 배포 실행, DB 초기화, AWS 리소스 생성은 **사용자 승인 후** 진행한다.

### 이슈별 기본 분담

| 성격 | 주 담당 | 보조 |
|---|---|---|
| 복잡한 멀티파일 설계 (인증, AI Job, RAG, 보안) | Claude Code | Codex |
| 명세 기반 CRUD·페이지네이션·단순 구현 | Codex | Claude Code |
| 프론트엔드 화면·컴포넌트·API 바인딩 | Antigravity | Claude Code |
| 테스트·빌드·API 정합성 검증 | Codex | — |
| 인프라·CI/CD·Docker·배포 흐름 | Claude Code | Codex |

---

## 3. 작업 범위 제한

### 백엔드 (`BookTown-V2-Backend/`)

**✅ 자유롭게 작업 가능**
- `src/main/java/com/booktown/` 하위 모든 Java 파일
- `src/test/java/com/booktown/` 하위 테스트 코드

**🔴 수정 금지 (사용자 승인 없이)**
- `compose.yaml`, `compose.prod.yaml`
- `src/main/resources/application.yml`, `application-test.yml`
- `build.gradle`
- `.env`, `.env.example`
- `gradlew`, `gradlew.bat`, `gradle/`

**🟡 수정 전 사용자 확인 필요**
- 외부 라이브러리 의존성 추가
- DB 스키마(Entity) 구조 변경
- Spring Security 설정 변경
- GitHub Actions workflow 파일

### 프론트엔드 (`BookTown-V2-Frontend/`)

**✅ 자유롭게 작업 가능**
- `src/` 하위 모든 TypeScript·TSX·CSS 파일

**🔴 수정 금지**
- `.env`, `.env.local`, 실제 API Key 포함 파일

**🟡 수정 전 사용자 확인 필요**
- `vite.config.ts`, `tailwind.config.ts` 등 빌드 설정
- 패키지 의존성 추가 (`package.json`)

---

## 4. 브랜치 컨벤션

```
main                         ← 배포 기준 브랜치 (직접 push 금지)
dev                          ← 개발 통합 브랜치 (PR 대상)
feat/issue-<num>-<desc>      ← 기능 개발
fix/issue-<num>-<desc>       ← 버그 수정
refactor/issue-<num>-<desc>  ← 구조 개선
docs/<desc>                  ← 문서 작업
infra/issue-<num>-<desc>     ← 인프라, 배포, CI/CD
```

- 브랜치 하나는 하나의 Issue 또는 하나의 목적만 가진다.
- `feat/*`, `fix/*` 등은 `dev`에서 분기하고 `dev`로 PR을 올린다.
- `dev` → `main` 반영은 배포 가능한 상태가 되었을 때만 한다.
- Squash Merge 사용 (커밋 히스토리 간소화).

---

## 5. 커밋 컨벤션

### 형식

```
<gitmoji> <type>: <작업 내용> [#이슈번호]
```

### 허용 타입

| Gitmoji | Type | 기준 |
|---|---|---|
| ✨ | `feat` | 사용자 기능 또는 API 추가 |
| 🔧 | `fix` | 버그 수정 |
| ♻️ | `refactor` | 동작 변경 없는 구조 개선 |
| 📝 | `docs` | README, Notion, API 문서, 주석 변경 |
| 🚀 | `infra` | EC2, Docker, Nginx, GitHub Actions, 배포 환경 |
| ✅ | `test` | 테스트 코드 추가/수정 |
| 🔒 | `chore` | 빌드 설정, 패키지 업데이트 |

### 규칙

- Gitmoji와 type의 의미가 서로 맞아야 한다.
- 하나의 커밋에는 하나의 의도만 담는다.
- 이슈가 연결된 작업은 제목 끝에 `[#이슈번호]`를 붙인다.
- Squash merge 시 최종 커밋 메시지도 같은 형식을 따른다.

---

## 6. PR 컨벤션

### 원칙

- PR은 하나의 Issue 또는 하나의 목적만 가진다.
- 변경 라인 300줄 이하 권장.
- `./gradlew test`, `./gradlew build` (백엔드) 또는 lint (프론트) 통과 후 PR 열기.
- CodeRabbit 리뷰가 있으면 필요한 부분만 반영하고 무조건 따르지 않는다.
- 구조 변경이 있으면 관련 문서도 함께 수정한다.
- Draft PR로 열지 말 것.

### PR 전 GitHub Issue 동기화

1. 관련 Issue 본문 체크박스 조회
2. 실제 코드·설정·테스트로 완료된 항목만 `[x]` 처리
3. EC2 배포, Grafana 확인 등 런타임 검증 항목은 PR 전 체크 금지
4. Issue 본문 하단에 브랜치명, 로컬 검증 결과, 남은 위험 기록 후 PR 생성

### PR 본문 템플릿

```markdown
## 연관 이슈
- Closes #이슈번호

## 작업 내용
- 변경 내용 요약

## 검증
- [ ] ./gradlew test (백엔드)
- [ ] ./gradlew build (백엔드)
- [ ] API 명세와 URL / Method / Status Code 일치 확인

## 문서
- [ ] README / Notion / API 문서 수정 필요 여부 확인

## 위험 요소
- 남은 위험 또는 후속 작업
```

---

## 7. 백엔드 컨벤션

### 기술 스택

| 분류 | 기술 |
|---|---|
| 웹 | Spring MVC |
| 보안 | Spring Security + JWT (jjwt) |
| ORM | Spring Data JPA + Hibernate |
| 캐시 | Spring Data Redis |
| 비정형 DB | Spring Data MongoDB |
| AI | Spring AI (OpenAI, ChromaDB) |
| HTTP 클라이언트 | WebClient |

### 데이터베이스 구성

| DB | 역할 | 포트 |
|---|---|---|
| MySQL | 회원, 도서, 퀴즈 결과, 좋아요, 북마크 | 3306 |
| Redis | Refresh Token, 이메일 인증 코드, 캐시 | 6379 |
| MongoDB | AI 요약 결과, 장면 메타데이터 | 27017 |
| ChromaDB | 벡터 임베딩 (RAG) | 8000 |

연결 정보는 반드시 `${환경변수명}` 형태로 참조한다. `application.yml`에 직접 값을 쓰지 않는다.

### 패키지 구조

```
com.booktown
├── global       ← 공통 응답, 예외, 보안 설정, 상수 (비즈니스 로직 금지)
├── auth         ← 인증·인가 (JWT, 이메일 인증)
├── user         ← 회원 관리
├── book         ← 도서 목록·상세·검색
├── summary      ← AI 줄거리/챕터 요약
├── quiz         ← 퀴즈 생성·채점·결과
├── scene        ← 장면 일러스트 생성·저장
└── admin        ← 관리자 전용 기능
```

새로운 도메인은 반드시 위 구조 하위에 추가한다. `global/`에 비즈니스 로직 금지.

### 네이밍 규칙

| 대상 | 규칙 | 예시 |
|---|---|---|
| 클래스 | PascalCase | `BookService`, `UserController` |
| 메서드·변수 | camelCase | `findBookById`, `userId` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 패키지 | lowercase | `com.booktown.book` |
| DB 테이블·컬럼 | snake_case | `created_at`, `user_id` |
| API 경로 | kebab-case | `/api/v1/book-summaries` |

### 클래스 이름 패턴

- Controller: `UserController`, `BookController`
- Service: `UserService`, `BookService`
- Repository: `UserRepository`, `BookRepository`
- Entity: 도메인 이름 그대로. 예: `User`, `Book`
- Enum: `UserRole`, `JobStatus`

### DTO 컨벤션

- Request/Response DTO는 `record` 우선 사용.
- `CreateXxxRequest`, `UpdateXxxRequest`, `XxxDetailResponse`, `XxxSummaryResponse`
- Entity를 API 응답으로 직접 노출하지 않는다.
- Request DTO와 Response DTO를 분리한다.

```java
public record CreateUserRequest(
    @NotBlank String email,
    @NotBlank String password
) {}
```

### Service 메서드 prefix

| prefix | 기준 |
|---|---|
| `create` | 새 리소스 생성 |
| `update` | 기존 리소스 수정 |
| `delete` | 삭제 처리 |
| `get` | 없으면 예외를 던지는 단건 조회 |
| `find` | 없을 수 있는 조회 또는 조건 검색 |
| `validate` | 검증 |

### API 응답 포맷

**성공 응답**
```json
{ "data": {}, "meta": null }
```

**페이지 응답**
```json
{
  "data": [],
  "meta": { "page": 0, "size": 20, "totalElements": 100, "totalPages": 5, "hasNext": true }
}
```

**실패 응답**
```json
{
  "error": {
    "code": "BOOK_NOT_FOUND",
    "message": "도서를 찾을 수 없습니다.",
    "fieldErrors": []
  },
  "traceId": "string"
}
```

- HTTP 상태 코드와 커스텀 에러 코드를 함께 사용한다.
- `@RestControllerAdvice`에서 공통 예외 처리.
- Spring 기본 에러 응답 노출 금지.
- 스택 트레이스·DB 접속정보·Secret을 응답 바디에 반환하지 않는다.

### JPA 규칙

- 연관관계는 단방향 우선, 양방향은 정말 필요한 경우만.
- fetch는 기본 `LAZY`. `@ManyToOne`, `@OneToOne`에 `fetch = FetchType.LAZY` 명시.
- N+1 문제가 예상되는 조회는 fetch join 또는 `@EntityGraph` 사용.
- Entity에 Lombok `@Data` 사용 금지.
- 기본 생성자는 JPA용으로 `protected` 사용.
- `@Transactional(readOnly = true)` — 읽기 전용 메서드에 필수.
- Entity를 API 응답으로 직접 반환하지 않는다.

### 인증 및 보안

- JWT Access Token + Refresh Token.
- Access Token: `Authorization: Bearer {accessToken}` 헤더.
- Refresh Token: Redis 저장 + **HttpOnly, Secure, SameSite 쿠키**로 전달. JSON 응답·localStorage 금지.
- 역할: `ROLE_USER`, `ROLE_ADMIN`.

**🔴 보안 금지사항**
- API Key, DB 비밀번호, JWT Secret 하드코딩 금지.
- `.env` 실제 값을 Git에 push 금지.
- 운영 환경에서 기본 JWT Secret으로 시작하지 않는다.
- 운영 JPA schema: `ddl-auto=validate`.

### 로깅 규칙

- 기본 레벨: `INFO`. 개발 환경에서 필요 시 `DEBUG` 허용.
- `System.out.println()` 금지 → Lombok `@Slf4j` 사용.
- 토큰, 비밀번호, 개인정보, API Key는 로그에 남기지 않는다.
- 장애 추적을 위해 traceId 사용.

---

## 8. 프론트엔드 컨벤션

### 기술 스택

| 분류 | 기술 |
|---|---|
| Core | React 19 + TypeScript + Vite |
| Styling | Tailwind CSS 4 |
| Routing | React Router v7 (BrowserRouter) |
| State/API | Axios, TanStack Query v5 |

### 핵심 규칙

- API 호출은 `src/api/client.ts`를 통해 처리. 직접 axios 인스턴스 생성 금지.
- v5 인증 정책 준수: HttpOnly 쿠키 기반 토큰 재발급.
- `any` 타입 사용 지양. TypeScript 강력한 타입 시스템 활용.
- ESLint 규칙 준수 (린트 에러 없도록 유지).
- 프로덕션 API base URL: `https://api.booktown.shop` (`VITE_API_BASE_URL` 환경변수 사용).
- CloudFront 정적 배포(SPA) 구조이므로 React Router 404/403 에러 처리 고려.

---

## 9. 데이터베이스 컨벤션

```sql
-- 테이블명: snake_case, 단수 명사
user, book, quiz, quiz_result

-- 컬럼명: snake_case
user_id, created_at, is_active

-- PK: id (BIGINT)
-- FK: {참조 테이블명}_id  →  user_id, book_id
-- 인덱스: idx_{테이블}_{컬럼}  →  idx_quiz_result_user_id
-- Boolean: is_ 접두사  →  is_active, is_correct
```

**공통 규칙**
- 모든 테이블에 `id`, `created_at` 필수. 필요 시 `updated_at` 추가.
- 소프트 삭제: DELETE 대신 `is_active = FALSE` 처리.
- FK는 `ON DELETE CASCADE` 또는 `ON DELETE SET NULL` 명시.
- profile: `local` / `test` / `prod`.

---

## 10. 테스트 및 품질

- 기능 추가에는 최소한의 검증 코드를 함께 작성한다.
- 리팩토링은 기존 동작을 보호하는 테스트를 우선 고려한다.
- PR에서는 `./gradlew test`, `./gradlew build` 결과를 확인한다.
- API 변경은 URL, Method, Status Code, 응답 포맷이 `API 명세 최종`과 일치해야 한다.
- SonarCloud는 테스트 대체가 아니라 정적 분석·커버리지·품질 게이트 도구로 사용한다.

---

## 11. 트러블슈팅 기록 규칙

문제 해결은 대화로 끝내지 않고 Notion `트러블슈팅 기록 — BookTown`에 남긴다.

기록 항목: 증상 / 재현 조건 / 원인 / 해결 방법 / 검증 결과 / 예방 조치 / 남은 위험

- Secret, DB 비밀번호, API Key, PEM 키 전문은 기록하지 않는다.
- 같은 문제가 2회 이상 반복되면 CI, PR 체크리스트, 테스트, 문서 규칙으로 승격한다.
- AI가 문제를 해결한 경우 담당 AI가 기록 초안을 남긴다.

---

## 12. 코드 수정 전 체크리스트

```
[ ] 현재 브랜치가 맞는가?
[ ] 수정 목적이 한 문장으로 정리되었는가?
[ ] 수정 범위를 제한했는가?
[ ] 테스트 기준을 정했는가?
[ ] 민감 파일(.env, Secret)을 제외했는가?
[ ] PR 단위로 나눌 수 있는가?
[ ] 변경 후 실행할 검증 명령이 있는가?
```

## 13. 코드 수정 후 체크리스트

```
[ ] 빌드가 성공하는가?
[ ] 테스트가 통과하는가?
[ ] 불필요한 파일이 수정되지 않았는가?
[ ] Secret이 노출되지 않았는가?
[ ] 기존 API 계약이 깨지지 않았는가?
[ ] 예외 처리가 적절한가?
[ ] GitHub Issue 체크박스를 갱신했는가?
[ ] PR 설명이 충분한가?
```

---

## 14. 에이전트별 작업 요청 템플릿

### Claude Code에게 맡길 때

```
BookTown-V2의 GitHub Issue #번호 작업을 맡아줘.
먼저 AGENTS.md, skills.md, WBS v5, API 명세 최종, 해당 Issue를 기준으로
현재 저장소 상태를 분석만 해줘.
실제 수정, commit, push, EC2 접속, Secret 변경, 배포 실행은 하지 말고
남은 작업, 위험한 부분, 필요한 사용자 승인 항목, 구현 순서, 검증 방법을 먼저 보고해줘.
```

### Codex에게 맡길 때

```
GitHub Issue #번호 기준으로 작업(또는 Claude 작업 결과)을 검증해줘.
API 명세 최종과 README 최종 기준으로 URL, HTTP method, 상태코드,
응답 포맷, 보안 규칙, 테스트 결과가 맞는지 확인하고
불일치 또는 누락된 체크리스트를 정리해줘.
필요하면 작은 수정만 하고, 큰 구조 변경은 새 이슈나 Claude 작업으로 분리해줘.
```

### Antigravity에게 맡길 때

```
BookTown-V2-Frontend의 Issue #번호 또는 화면 기능을 구현해줘.
AGENTS.md 프론트엔드 컨벤션과 API 명세 최종을 기준으로
React + TypeScript + Tailwind CSS로 구현하고,
백엔드 폴더(BookTown-V2-Backend/)는 수정하지 말고
.env 실제 값과 Secret은 사용하지 말아줘.
```
