## 🎯 배경 / 목표
서버리스 역량을 얹되, **import API 자체는 Lambda로 옮기지 않는다**(긴 실행시간·상태관리·Java cold start·VPC/NAT 비용으로 부적합). 대신 짧고 이벤트성인 사이드 작업을 Lambda로 분리한다. 로드맵 우선순위 **3순위(Phase 2)**.

## 📋 작업 내용
- [ ] 대상 선정: S3 이벤트 기반 표지 썸네일 생성 **또는** EventBridge 기반 외부 메타데이터 동기화
- [ ] Python/Node 런타임으로 Lambda 구현 (VPC 미접속)
- [ ] S3 / EventBridge 트리거 연결
- [ ] IAM 최소권한 정책 작성 (해당 버킷/리소스만)
- [ ] Terraform으로 Lambda·트리거·IAM 프로비저닝 (issue #1 모듈과 연계)

## 📦 산출물
- Lambda 코드
- S3/EventBridge trigger 구성
- Terraform 프로비저닝 코드
- IAM 최소권한 정책 문서

## ✅ 완료 조건 (DoD)
- 트리거 이벤트 발생 시 Lambda가 정상 실행되고 결과 산출물 확인
- IAM 정책이 최소권한(대상 리소스 한정)으로 검증됨
- VPC 미접속·짧은 실행시간 설계가 문서화됨

## 💰 비용 주의
VPC 미접속, 짧은 실행 시간, 최소 권한으로 설계. NAT/VPC 연결로 비용이 새지 않도록 한다.

## 🔗 참고
- 로드맵: [프로젝트별 클라우드 보강 로드맵](https://app.notion.com/p/399827b32761819983e6deb14e777802) — BookTown 3순위
- 선행: #1 (Terraform 모듈)
