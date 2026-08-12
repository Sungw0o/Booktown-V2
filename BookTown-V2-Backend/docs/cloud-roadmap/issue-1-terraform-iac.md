## 🎯 배경 / 목표
BookTown은 K8s 대신 **단일 EC2 + 매니지드 AWS** 조합을 유지한다(비용·운영 복잡도 판단). 대신 인프라를 코드로 재현 가능하게 만들기 위해 Terraform으로 기본 리소스를 IaC 화한다. 로드맵 우선순위 **1순위(Phase 1)**.

## 📋 작업 내용
- [ ] `infra/terraform/booktown` 모듈 구조 설계 (backend state, variables, outputs)
- [ ] S3(정적 호스팅) + CloudFront 리소스 코드화
- [ ] EC2 + Security Group 코드화
- [ ] IAM Role(GitHub Actions OIDC role-to-assume 포함) 코드화
- [ ] Route53 레코드 코드화
- [ ] GitHub Actions에 `terraform fmt -check` + `terraform plan` 검증 job 추가
- [ ] `apply`는 비용·권한 이슈로 수동 승인(workflow_dispatch) 단계로 분리

## 📦 산출물
- `infra/terraform/booktown/` 디렉터리 구조
- README: 리소스 목록, 변수, `apply`/`destroy` 절차, 예상 비용 메모
- `terraform plan` 캡처 또는 CI 로그

## ✅ 완료 조건 (DoD)
- `terraform plan`이 CI에서 통과
- 기존 수동 구성 리소스와 코드가 1:1로 매핑됨
- `destroy` 절차와 예상 비용이 README에 명시됨

## 🗣️ 면접 문장
> BookTown은 K8s보다 단일 EC2 + 매니지드 서비스 조합이 비용·운영 복잡도 측면에서 맞다고 판단했습니다. 대신 Terraform으로 확장 가능한 AWS 리소스를 코드화해 재현성을 확보했습니다.

## 💰 비용 주의
`apply`는 상시 실행하지 않는다. 필요 시 수동 승인 후 실행하고, Terraform에 `destroy` 절차·예상 비용 메모를 반드시 포함한다.

## 🔗 참고
- 로드맵: [프로젝트별 클라우드 보강 로드맵](https://app.notion.com/p/399827b32761819983e6deb14e777802) — BookTown 1순위
- 정체성 축: 매니지드 AWS 운영 자동화
