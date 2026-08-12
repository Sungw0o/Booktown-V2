# Codex 지시서 — GitHub 이슈 4개 생성

## 목표
`docs/cloud-roadmap/` 에 준비된 본문으로 저장소 **`Sungw0o/Booktown-V2`** 에
라벨 6개를 보장한 뒤 이슈 4개를 생성한다.

## 작업 폴더
```
C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\docs\cloud-roadmap
```
여기에 이미 다음 파일이 있음 (본문 새로 쓰지 말고 그대로 사용):
- `issue-1-terraform-iac.md`
- `issue-2-nginx-blue-green.md`
- `issue-3-lambda-pipeline.md`
- `issue-4-3tier-asg.md`
- `create-issues.ps1` (아래 스펙과 동일한 일괄 생성 스크립트)

## 사전 확인
1. `gh --version` — GitHub CLI 설치돼 있는지. 없으면 설치(`winget install --id GitHub.cli`) 후 진행.
2. `gh auth status` — 로그인돼 있는지. 안 돼 있으면 `gh auth login` (repo 권한 필요).
3. 위 인증은 **사람이 직접** 해야 함. 코덱스가 토큰/비번을 대신 입력하지 말 것.

## 해야 할 일

### 1) 라벨 보장 (`gh label create ... --force`)
| name | color | description |
|---|---|---|
| cloud | 1D76DB | 클라우드/인프라 로드맵 |
| infra | 0E8A16 | 인프라·배포 |
| enhancement | A2EEEF | 기능 추가 |
| phase-1 | 5319E7 | 로드맵 Phase 1(신뢰도) |
| phase-2 | B60205 | 로드맵 Phase 2(차별화) |
| phase-3 | FBCA04 | 로드맵 Phase 3(고난도) |

### 2) 이슈 4개 생성 (`gh issue create --repo Sungw0o/Booktown-V2 --title ... --body-file ... --label ...`)
| 제목 | 본문 파일 | 라벨 |
|---|---|---|
| 🚀 infra: Terraform으로 AWS 기본 리소스 IaC 코드화 | issue-1-terraform-iac.md | cloud,infra,phase-1 |
| 🚀 infra: 단일 EC2 + Nginx upstream 기반 Blue-Green 무중단 배포 | issue-2-nginx-blue-green.md | cloud,infra,phase-1 |
| ✨ feat: Lambda 사이드 파이프라인 (S3 이벤트/EventBridge) | issue-3-lambda-pipeline.md | cloud,enhancement,phase-2 |
| 🚀 infra: 3-tier + ASG Terraform 레퍼런스 아키텍처 | issue-4-3tier-asg.md | cloud,infra,phase-3 |

## 실행 방법 (택1)

### A. 준비된 스크립트 사용 (권장)
`create-issues.ps1` 이 위 스펙 그대로임. PowerShell 7(`pwsh`)이 없으면 Windows PowerShell로:
```powershell
cd C:\Users\SSAFY\Desktop\Booktown-V2\BookTown-V2-Backend\docs\cloud-roadmap
powershell -ExecutionPolicy Bypass -File .\create-issues.ps1 -DryRun   # 미리보기(생성 안 함)
powershell -ExecutionPolicy Bypass -File .\create-issues.ps1           # 실제 생성
```
Windows PowerShell 5.1에서 제목 이모지가 깨지면 `gh` 명령을 직접 호출하는 B안으로.

### B. gh CLI 직접 호출
위 "해야 할 일" 표 그대로 `gh label create`(6개) → `gh issue create`(4개) 순서로 실행.
`--body-file` 로 각 md 본문을 넣을 것.

## 완료 조건
- 라벨 6개가 저장소에 존재.
- 이슈 4개가 생성되고 각각 지정 라벨이 붙음.
- 확인: `gh issue list --repo Sungw0o/Booktown-V2 --label cloud` 에 4개가 보임.
- 생성된 이슈 URL 4개를 결과로 출력.

## 주의
- 본문(`issue-*.md`)은 수정하지 말 것. 제목/라벨도 위 표에서 바꾸지 말 것.
- 중복 실행 주의: 이슈는 재실행 시 중복 생성됨. 먼저 `-DryRun`으로 확인 후 1회만 실제 실행.
- 저장소 이름이 `Sungw0o/Booktown-V2` 가 맞는지 확인(대소문자 포함). 다르면 `--repo` 값 교정.
