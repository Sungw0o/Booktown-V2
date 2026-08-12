## 🎯 배경 / 목표
시장에서 매우 흔한 **ALB → EC2 ASG → RDS 3-tier + 오토스케일링** 구조를 Terraform 레퍼런스로 작성한다. 상시 운영하지 않고 필요할 때만 apply/destroy 하며, "운영 중인 구조"가 아니라 **"확장 전환 가능한 참조 아키텍처"**로 표현한다. 로드맵 우선순위 **4순위(Phase 3)**.

## 📋 작업 내용
- [ ] VPC / 서브넷(public·private) / 라우팅 코드화
- [ ] ALB + Target Group + 리스너 코드화
- [ ] EC2 Launch Template + Auto Scaling Group 코드화
- [ ] RDS(Multi-AZ 옵션) 코드화
- [ ] apply/destroy 절차와 예상 비용 메모 작성
- [ ] 포트폴리오 표기: "레퍼런스 구현"임을 명확히 구분

## 📦 산출물
- `infra/terraform/booktown-3tier-ref/` 모듈
- 아키텍처 다이어그램
- apply → smoke → destroy 검증 로그

## ✅ 완료 조건 (DoD)
- `terraform plan` 통과, 필요 시 apply→smoke→destroy 1회 검증
- "상시 운영"이 아닌 "참조 아키텍처"로 문서/포트폴리오에 표기
- 예상 비용과 destroy 절차 명시

## 💰 비용 주의
ALB·ASG·RDS·NAT Gateway는 실수로 켜두면 비용이 빠르게 발생한다. 증거 확보 후 즉시 destroy. **운영 경험처럼 과장하면 신뢰를 깎으므로** 표현에 주의.

## 🔗 참고
- 로드맵: [프로젝트별 클라우드 보강 로드맵](https://app.notion.com/p/399827b32761819983e6deb14e777802) — BookTown 4순위 (Phase 3, 시간 여유 시)
- 선행: #1 (Terraform 모듈 구조 재사용)
