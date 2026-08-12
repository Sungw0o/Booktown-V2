# 클라우드 보강 로드맵 → GitHub 이슈

[프로젝트별 클라우드 보강 로드맵](https://app.notion.com/p/399827b32761819983e6deb14e777802)의
**BookTown(매니지드 AWS 운영 축)** 항목을 이 저장소 이슈로 등록하기 위한 자료.
(HighFive/K8s 항목은 HighFive 저장소 소관이라 제외)

## 이슈 목록
| # | 제목 | 우선순위 | Phase | 라벨 |
|---|---|---|---|---|
| 1 | 🚀 infra: Terraform으로 AWS 기본 리소스 IaC 코드화 | 1순위 | 1 | cloud, infra, phase-1 |
| 2 | 🚀 infra: 단일 EC2 + Nginx upstream 기반 Blue-Green 무중단 배포 | 2순위 | 1 | cloud, infra, phase-1 |
| 3 | ✨ feat: Lambda 사이드 파이프라인 (S3 이벤트/EventBridge) | 3순위 | 2 | cloud, enhancement, phase-2 |
| 4 | 🚀 infra: 3-tier + ASG Terraform 레퍼런스 아키텍처 | 4순위 | 3 | cloud, infra, phase-3 |

## 실행 방법
```powershell
cd docs\cloud-roadmap
pwsh ./create-issues.ps1 -DryRun   # 미리보기 (아무것도 생성 안 함)
pwsh ./create-issues.ps1           # 실제 생성 (라벨 + 이슈 4개)
```
- 사전 조건: `gh auth login` 완료(repo 권한).
- 스크립트가 라벨을 먼저 보장(`--force`)한 뒤 이슈를 생성한다.
- Windows PowerShell(5.1)에서 이모지 인코딩 문제가 나면 `pwsh`(PowerShell 7)로 실행.

## 참고
- 제목은 저장소 커밋/이슈 컨벤션(`🚀 infra:`, `✨ feat:`)을 따랐다.
- 각 이슈 본문(`issue-*.md`)에는 배경·작업내용·산출물·완료조건(DoD)·면접 문장·비용 주의가 들어 있다.
- 표현 원칙(로드맵): "써봤다"보다 "왜 이 구조를 선택했는지", 운영 중 vs 레퍼런스 구분, 비용 절감은 워크로드 규모에 맞춘 선택으로 설명.
