# BookTown V2

AI 기반 고전문학 인터랙티브 독서 플랫폼입니다.  
단순히 책 목록을 보여주는 서비스가 아니라, 공개 도서를 수집하고 원문을 정제한 뒤 AI 요약, 장면 구성, 퀴즈, 독서 경험으로 연결하는 것을 목표로 만들었습니다.

## 한 줄 요약

> 고전문학 원문을 가져와 AI로 읽기 쉽게 재가공하고, 사용자가 요약·장면·퀴즈를 통해 책을 더 쉽게 탐색하도록 돕는 풀스택 서비스

## 만들고 싶었던 것

- 긴 고전 원문을 사용자가 부담 없이 접근할 수 있는 형태로 재가공
- 관리자 페이지에서 도서 수집, 원문 업로드, AI 처리 상태를 직접 관리
- AI 요약과 장면 기반 읽기 경험을 하나의 흐름으로 제공
- 실제 배포 환경을 고려한 인증, 모니터링, CI/CD, 보안 검증 구성

## 주요 기능

| 영역 | 구현 내용 |
|---|---|
| 도서 수집 | Gutendex 기반 공개 도서 검색, 메타데이터·표지·원문 수집 |
| 원문 처리 | TXT 다운로드, 본문 정제, 챕터 분리, 불필요한 상하단 문구 제거 |
| AI 요약 | Gemini 기반 책 소개, 줄거리, 챕터 요약 생성 |
| 독서 경험 | 도서 상세, 지금 읽으러 가기, 수록 목차, 퀴즈 풀이 |
| 관리자 | 도서 등록, 크롤링/파싱 Job 상태 확인, AI 생성 요청 |
| 인증/보안 | JWT, Refresh Token, HttpOnly Cookie, Cloudflare Turnstile |
| 운영 | Docker Compose, GitHub Actions, Prometheus/Grafana, k6 smoke/load test |

## 기술 스택

| 구분 | 기술 |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, TanStack Query |
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA |
| Database | MySQL, Redis, MongoDB, ChromaDB |
| AI | Gemini API, Spring AI, Vector Store 기반 RAG 구조 |
| Infra | Docker Compose, AWS EC2, S3, CloudFront, Nginx |
| DevOps | GitHub Actions, Prometheus, Grafana, k6 |

## 시스템 구조

```text
User
  -> CloudFront / Frontend
  -> Nginx / Backend API
  -> Spring Boot
      -> MySQL: 회원, 도서, 퀴즈
      -> Redis: Refresh Token, 캐시
      -> MongoDB: 요약, 장면 메타데이터
      -> ChromaDB: 원문 임베딩 검색
      -> Gemini API: 요약 및 이미지 생성 후보
```

## AI 도서 처리 흐름

```text
Gutendex 검색
  -> 도서 메타데이터/표지/원문 URL 수집
  -> 원문 TXT 비동기 다운로드
  -> 본문 정제 및 챕터 분리
  -> 제목/소개/요약 한국어 재가공
  -> 장면·퀴즈·읽기 페이지에서 활용
```

현재는 비용과 quota를 고려해 Gutendex 원본 표지를 기본으로 사용하고, AI 표지 생성은 선택 기능으로 분리했습니다.

## 운영 관점에서 신경 쓴 부분

- GitHub Actions로 프론트엔드/백엔드 빌드와 배포 흐름 자동화
- EC2 단일 서버에서도 MySQL, Redis, MongoDB, ChromaDB를 Docker Compose로 운영
- Prometheus/Grafana로 API와 인프라 상태를 관찰할 수 있는 구조 추가
- k6로 `/health` 같은 핵심 엔드포인트의 기본 응답성과 배포 후 상태를 확인
- Cloudflare Turnstile로 로그인/회원가입 봇 요청 방어
- Grafana는 Cloudflare Access로 한 번 더 보호

## 로컬 실행 개요

```bash
# Backend
cd BookTown-V2-Backend
./gradlew bootRun

# Frontend
cd BookTown-V2-Frontend
npm install
npm run dev
```

실제 실행에는 MySQL, Redis, MongoDB, ChromaDB 및 AI API Key 환경변수가 필요합니다. 민감 정보는 저장소에 커밋하지 않습니다.

## 프로젝트에서 보여주고 싶은 역량

- AI API를 단순 호출이 아니라 서비스 흐름 안에 Job 구조로 통합한 경험
- 공개 데이터 수집, 원문 정제, AI 재가공 파이프라인 설계 경험
- JWT, HttpOnly Cookie, Turnstile을 조합한 웹 인증·보안 설계
- EC2, Docker Compose, CloudFront, GitHub Actions 기반 배포 경험
- Prometheus/Grafana/k6를 활용한 운영 관점의 검증 경험

## 앞으로 개선하고 싶은 것

- AI 이미지 생성 비용과 quota를 고려한 모델 선택 및 fallback 고도화
- 요약 결과 품질 평가 기준 추가
- 대량 도서 처리 시 Job 큐와 재시도 정책 강화
- 쿠폰/선착순 이벤트 같은 동시성 성능 테스트용 별도 기능 또는 미니 프로젝트 분리

