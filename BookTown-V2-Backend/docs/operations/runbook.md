# BookTown Backend Local Operations Runbook

## 목적

BookTown 백엔드를 로컬 Docker Compose 환경에서 운영하고 Prometheus/Grafana로 관측하기 위한 최소 운영 절차를 정리한다. 이 문서는 실제 유료 클라우드 리소스를 만들지 않고, 로컬 환경에서 클라우드 운영 역량을 재현하는 데 초점을 둔다.

## 구성 요소

- Backend: Spring Boot API, app profile로 선택 실행
- MySQL: 핵심 정형 데이터
- Redis: 인증 토큰과 캐시
- MongoDB: AI 결과 메타데이터
- ChromaDB: 벡터 저장소
- Prometheus: Actuator metrics 수집
- Grafana: 대시보드 시각화
- Loki: 로그 저장소
- Node Exporter/cAdvisor: 호스트와 컨테이너 지표
- k6: health endpoint 부하 테스트

## 로컬 기동

의존 서비스만 실행한다.

```bash
scripts/local-infra.sh up
```

백엔드 컨테이너까지 함께 실행한다.

```bash
scripts/local-infra.sh up-app
```

모니터링 스택까지 함께 실행한다.

```bash
scripts/local-infra.sh monitor
```

## 접속 주소

- API: http://localhost:8080/api/v1
- Readiness: http://localhost:8080/api/v1/health/readiness
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001
- Loki: http://localhost:3100
- Node Exporter: http://localhost:9100
- cAdvisor: http://localhost:8082

Grafana 기본 로컬 계정은 환경변수로 바꿀 수 있다.

```bash
GRAFANA_ADMIN_USER=admin GRAFANA_ADMIN_PASSWORD=change-me scripts/local-infra.sh monitor
```

## Health Check

```bash
scripts/local-infra.sh health
docker compose ps
```

장애가 있으면 먼저 컨테이너 상태와 백엔드 로그를 확인한다.

```bash
scripts/local-infra.sh ps
scripts/local-infra.sh logs
```

## k6 부하 테스트

백엔드가 실행 중인 상태에서 readiness endpoint를 점검한다.

```bash
docker run --rm -i --network host grafana/k6:0.54.0 run - < performance/k6/health-check.js
```

Docker Desktop 환경에서 `--network host`가 기대대로 동작하지 않으면 host gateway 대신 직접 URL을 지정한다.

```bash
docker run --rm -i grafana/k6:0.54.0 run -e BASE_URL=http://host.docker.internal:8080/api/v1 - < performance/k6/health-check.js
```

부하 강도는 환경변수로 조정한다.

```bash
K6_VUS=20 K6_DURATION=1m docker run --rm -i grafana/k6:0.54.0 run - < performance/k6/health-check.js
```

## Prometheus/Grafana 점검

Prometheus Targets에서 다음 job을 확인한다.

- `booktown-backend`
- `prometheus`
- `node-exporter`
- `cadvisor`
- `loki`

Grafana에서는 자동 프로비저닝된 Prometheus/Loki datasource와 BookTown Overview 대시보드를 확인한다.

## 종료와 정리

컨테이너만 중지한다.

```bash
scripts/local-infra.sh down
```

볼륨까지 삭제해 로컬 데이터를 초기화한다.

```bash
scripts/local-infra.sh clean
```

## 장애 대응 순서

1. `scripts/local-infra.sh ps`로 unhealthy 컨테이너를 찾는다.
2. `docker compose logs <service>`로 원인을 확인한다.
3. MySQL, Redis, MongoDB, ChromaDB healthcheck가 먼저 정상인지 본다.
4. 백엔드 readiness가 503이면 의존 서비스 연결 정보를 확인한다.
5. Prometheus target이 down이면 `backend:8081` 또는 `host.docker.internal:8081` 연결 방식을 확인한다.
6. Grafana datasource가 실패하면 Prometheus/Loki 컨테이너 readiness를 먼저 확인한다.

## 보안 주의

- 실제 API Key, DB 비밀번호, JWT Secret은 커밋하지 않는다.
- 로컬 Compose 기본값은 개발 편의용이며 운영 Secret으로 사용하지 않는다.
- 운영 서버에서는 모니터링 포트를 직접 공개하지 않고 SSH 터널 또는 Cloudflare Access 같은 외부 접근 제어를 둔다.
