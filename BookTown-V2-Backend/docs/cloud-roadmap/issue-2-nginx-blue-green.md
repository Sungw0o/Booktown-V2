## 🎯 배경 / 목표
비용상 ALB 기반 Blue-Green 대신, **단일 EC2 내부에서 Nginx upstream 전환**으로 무중단 배포를 구현한다. 현재 CD는 컨테이너 교체 후 readiness 폴링·롤백까지 있으므로, 여기에 blue/green 스택 분리를 얹는다. 로드맵 우선순위 **2순위(Phase 1)**.

## 📋 작업 내용
- [ ] `compose`에 blue/green 스택 분리(포트/네트워크/컨테이너명 구분)
- [ ] Nginx upstream(또는 symlink) 기반 활성 스택 전환 스크립트 작성
- [ ] 배포 workflow: green 기동 → readiness 통과 확인 → upstream 전환 → blue 정리
- [ ] readiness 실패 시 이전(활성) upstream 유지 = 자동 rollback
- [ ] 전환 중 커넥션 드레이닝(기존 요청 유실 방지) 확인

## 📦 산출물
- 배포 workflow (`.github/workflows`)
- blue/green 포트·네트워크 구조 문서
- 성공 시나리오 / readiness 실패 롤백 시나리오 로그

## ✅ 완료 조건 (DoD)
- 배포 중 `GET /api/v1/health/readiness` 및 대표 조회 API가 5xx 없이 유지
- readiness 실패를 인위로 유발했을 때 활성 스택이 전환되지 않고 유지됨
- 전환 흐름(배포 전 → green 기동 → 전환 → blue 종료)이 로그로 남음

## 🗣️ 면접 문장
> 비용상 ALB 기반 Blue-Green 대신 단일 EC2 내부에서 Nginx upstream을 전환했습니다. 작은 서비스 규모에서는 비용 대비 가장 현실적인 무중단 배포 방식이라고 판단했습니다.

## 🔗 참고
- 로드맵: [프로젝트별 클라우드 보강 로드맵](https://app.notion.com/p/399827b32761819983e6deb14e777802) — BookTown 2순위
- 연계: 기존 CD readiness 폴링·rollback 흐름 확장
