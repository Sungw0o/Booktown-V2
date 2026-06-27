# BookTown Backend V2 — AI 에이전트 공통 지침서

> 이 파일은 AI 에이전트(Claude, Codex 등)가 백엔드 코드를 작성하거나 수정하기 전에
> **반드시 먼저 읽어야 하는 핵심 규칙서**입니다. 모든 지침은 팀 컨벤션 및 인프라 결정사항을 반영합니다.

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 서비스명 | BookTown (책고을) |
| 설명 | AI 기반 고전문학 인터랙티브 독서 플랫폼 |
| 백엔드 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.0 |
| 빌드 도구 | Gradle |
| 기본 포트 | 8080 |
| API Base URL | `/api/v1` (context-path) |

---

## 2. 기술 스택

| 분류 | 기술 |
|---|---|
| 웹 | Spring MVC (spring-boot-starter-webmvc) |
| 보안 | Spring Security + JWT (jjwt 0.13.0) |
| ORM | Spring Data JPA + Hibernate |
| 캐시/세션 | Spring Data Redis |
| 비정형 DB | Spring Data MongoDB |
| HTTP 클라이언트 | WebClient |
| AI | Spring AI (OpenAI, ChromaDB Vector Store) |
| 유효성 검사 | Spring Validation |
| 이메일 | Spring Mail |
| 인증 확장 | Spring OAuth2 Client |
| 문서화 | Lombok |

---

## 3. 데이터베이스 구성

> Phase 1 기준: **EC2 단일 인스턴스에 Docker Compose로 전부 운영**

| DB | 역할 | 포트 |
|---|---|---|
| MySQL | 핵심 정형 데이터 (회원, 도서, 퀴즈 결과, 좋아요, 북마크) | 3306 |
| Redis | Refresh Token, 이메일 인증 코드, 캐시 | 6379 |
| MongoDB | AI 요약 결과, 장면 일러스트 메타데이터 저장 | 27017 |
| ChromaDB | 벡터 임베딩 저장 (RAG 파이프라인) | 8000 |

**연결 정보는 항상 환경변수로 참조한다. `application.yml`에 명시된 `${...}` 형태를 따른다.**

---

## 4. 패키지 구조 (도메인 기반)

```
com.booktown
├── auth/           # 인증·인가 (JWT, OAuth2, 이메일 인증)
├── user/           # 회원 관리
├── book/           # 도서 목록·상세·검색
├── summary/        # AI 줄거리/챕터 요약
├── illustration/   # 장면 일러스트 생성·저장
├── quiz/           # 퀴즈 생성·채점·결과
├── bookmark/       # 북마크·찜하기
├── like/           # 좋아요
├── notice/         # 공지사항
├── admin/          # 관리자 전용 기능
├── global/         # 공통 (예외 처리, 응답 래퍼, 보안 설정, 상수)
└── controller/     # 현재 임시 위치 (HealthController 등) → 추후 도메인으로 이동
```

> 새로운 기능은 반드시 **도메인 폴더 하위**에 작성한다. `global/` 폴더에 공통 클래스를 두되, 비즈니스 로직은 포함하지 않는다.

---

## 5. API 응답 포맷 (필수 준수)

모든 컨트롤러는 아래 포맷을 **반드시** 따라야 한다.

### 성공 응답
```json
{
  "data": { ... }
}
```

### 실패 응답
```json
{
  "error": "CUSTOM_ERROR_CODE",
  "message": "사람이 읽을 수 있는 오류 메시지"
}
```

- HTTP 상태 코드와 커스텀 에러 코드를 **함께** 사용한다.
- 공통 예외 처리는 `@RestControllerAdvice`를 활용한 글로벌 예외 핸들러로 처리한다.
- 절대로 Spring의 기본 에러 응답 형태(timestamp, path 포함)를 그대로 내보내지 않는다.

---

## 6. 인증 및 보안 규칙

- 인증 방식: **JWT** (Access Token + Refresh Token)
  - Access Token 만료 시 `/api/v1/auth/reissue`로 재발급
  - Refresh Token은 **Redis**에 저장
- 소셜 로그인: Google, Kakao, Naver (OAuth2)
- 인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더 필요
- 역할(Role): `USER`, `ADMIN` (Spring Security로 접근 제어)

### 🔴 보안 금지사항 (절대 위반 금지)
- API Key, DB 비밀번호, JWT Secret 등을 **코드에 직접 하드코딩** 금지
- 모든 민감 정보는 `${환경변수명}` 형태로 참조할 것
- `.env` 파일과 `application.yml`의 기존 환경변수 키 이름을 **임의로 변경하지 말 것**

---

## 7. 코드 컨벤션

### 네이밍
| 대상 | 규칙 | 예시 |
|---|---|---|
| 클래스 | PascalCase | `BookService`, `UserController` |
| 메서드·변수 | camelCase | `findBookById`, `userId` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| DB 컬럼 | snake_case | `created_at`, `user_id` |
| API 경로 | kebab-case | `/api/v1/book-summaries` |

### JPA 규칙
- **N+1 문제 방지**: 연관 데이터 조회 시 `@EntityGraph` 또는 `JOIN FETCH` 사용
- `FetchType.EAGER` 사용 금지 (기본적으로 `LAZY` 사용)
- 양방향 연관관계는 꼭 필요한 경우만 사용, `mappedBy` 명확히 설정
- 엔티티에 비즈니스 로직 직접 작성 지양 → Service 레이어로 분리

### 트랜잭션
- Service 레이어에 `@Transactional` 적용
- 읽기 전용 메서드는 `@Transactional(readOnly = true)` 사용
- 트랜잭션 범위 밖에서 지연 로딩(lazy) 접근 금지 (LazyInitializationException 주의)

### 기타
- 모든 예외는 커스텀 예외 클래스로 감싸서 글로벌 핸들러에서 처리
- `System.out.println()` 사용 금지 → Lombok `@Slf4j` 로그 사용
- DTO와 Entity를 명확히 분리 (Entity를 응답 바디에 직접 노출 금지)

---

## 8. 브랜치 전략

```
main    ← 최종 배포 브랜치
dev     ← 개발 통합 브랜치 (feat/fix → dev → main)
feat/기능명    ← 기능 개발 브랜치
fix/버그명     ← 버그 수정 브랜치
```

- 작업 완료 후 `dev` 브랜치로 PR 제출
- 머지 방식: Squash Merge (커밋 히스토리 정리)
- PR 템플릿 사용, CodeRabbit 리뷰 반영

### PR 전 GitHub Issue 동기화 규칙

PR을 열기 전에 반드시 관련 GitHub Issue 본문을 다시 읽고 체크박스를 갱신한다.

- 실제 코드, 설정, 문서, 테스트로 완료된 항목만 `[x]` 처리한다.
- EC2 배포 후 확인, Grafana 화면 확인, 백업 복구 성공처럼 런타임 검증이 필요한 항목은 PR 전에는 체크하지 않는다.
- Issue 본문 하단에 브랜치명, 커밋 해시, 로컬 검증 결과, 남은 검증을 적는다.
- 그 다음 PR을 생성한다.

예시:

```plain text
작업 완료
  -> ./gradlew test, docker compose config 등 로컬 검증
  -> GitHub Issue 체크박스 갱신
  -> 브랜치/커밋/검증 결과 기록
  -> PR 생성
  -> CodeRabbit 리뷰 대응
```

---

## 9. 작업 범위 제한 (하네스 규칙)

에이전트는 아래 규칙을 **반드시** 준수한다:

### ✅ 작업 가능 영역
- `src/main/java/com/booktown/` 하위 모든 Java 파일
- `src/test/java/com/booktown/` 하위 테스트 코드

### 🔴 무단 수정 금지 영역
- `compose.yaml` → DB 환경 변경 시 반드시 사람에게 확인 요청
- `src/main/resources/application.yml` → 환경변수 키 변경 금지
- `src/main/resources/application-test.yml`
- `build.gradle` → 의존성 추가 필요 시 사람에게 확인 후 진행
- `.env`, `.env.example`
- `gradlew`, `gradlew.bat`, `gradle/`

### 🟡 변경 전 반드시 확인 요청
- 새로운 외부 라이브러리 의존성 추가
- DB 스키마(Entity) 구조적 변경
- Spring Security 설정 파일 수정

---

## 10. 헬스체크 API

`GET /api/v1/health` 엔드포인트가 이미 구현되어 있음.
MySQL, Redis, MongoDB 상태를 반환한다. 이 파일은 수정하지 말 것.

---

## 11. 주요 도메인 기능 목록

| 도메인 | 핵심 기능 |
|---|---|
| auth | 이메일 회원가입/로그인, JWT 발급, 소셜로그인(OAuth2), 비밀번호 재설정 |
| user | 내 프로필 조회·수정, 활동 기록 |
| book | 도서 목록·상세 조회, 제목/저자 검색, 장르 필터 |
| summary | 도서·챕터별 AI 요약 생성·저장·재생성 (OpenAI → MongoDB) |
| illustration | 주요 장면 추출·이미지 생성·저장·재생성 (DALL-E → S3 예정) |
| quiz | 객관식/주관식 퀴즈 생성·제출·채점·해설·결과 저장 |
| bookmark | 도서 찜하기 (찜 추가·취소·목록 조회) |
| like | 도서 좋아요 (추가·취소·수 조회) |
| notice | 공지사항 목록·상세 조회 |
| admin | 도서 등록·원문 업로드, 사용자 상태 관리, AI 결과 관리 |
