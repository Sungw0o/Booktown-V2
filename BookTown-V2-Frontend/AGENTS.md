# AGENTS.md — 프론트엔드 에이전트 지침

본 파일은 **BookTown Frontend V2** 프로젝트에서 프론트엔드 개발 에이전트가 준수해야 할 핵심 지침을 정의합니다.

## 🚨 시작 전 필수 사항

**코드 작성 또는 수정을 시작하기 전에 다음을 확인하십시오:**
1. 프로젝트의 프론트엔드 기술 스택 및 구조를 파악합니다.
2. Notion `책고을 WBS 작업 관리 최종` 데이터베이스의 담당 기능 이슈 번호와 명세를 확인합니다.
3. API 호출 구현 시 `src/api/client.ts`를 활용하고, 반드시 v5 인증 설계 정책(HttpOnly 쿠키 기반 토큰 재발급)을 준수합니다.

---

## 💻 기술 스택 및 라이브러리

- **Core**: React 19 + TypeScript + Vite
- **Styling**: Tailwind CSS 4
- **Routing**: React Router v7 (BrowserRouter)
- **State/API**: Axios, TanStack Query v5

---

## 🛠 핵심 개발 규칙

1. **이슈 기반 브랜치 & 커밋 컨벤션 준수**:
   - 브랜치명: `feat/issue-<num>-<desc>` 또는 `infra/issue-<num>-<desc>`
   - 커밋 메시지: `<emoji> <type>: <message> [#<issue_number>]`
2. **깃허브 이슈 연동 규칙**:
   - **커밋 후 푸시할 때마다** 해당 작업과 연관된 GitHub Issue 본문의 체크박스(Todo List)를 조회하여, 완료된 구현 사항들을 즉시 체크(`[x]`) 처리합니다.
3. **배포 환경 고려**:
   - CloudFront를 통한 정적 배포 구조(SPA)이므로 React Router의 404/403 에러 처리가 배포 환경에서 구성되어 있습니다.
   - 프로덕션 API base URL은 `https://api.booktown.shop`이며, 환경변수 `VITE_API_BASE_URL`로 분리 관리합니다.
4. **코드 품질**:
   - `any` 타입 사용을 지양하고 TypeScript의 강력한 타입 시스템을 활용합니다.
   - ESLint 규칙을 준수하여 린트 에러가 없도록 유지합니다.
