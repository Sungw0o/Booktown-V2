# BookTown Monitoring

Prometheus, Grafana, Loki, Grafana Alloy 운영 설정입니다.

## 로컬 모니터링 빠른 실행

로컬 의존 서비스만 실행합니다.

```bash
scripts/local-infra.sh up
```

백엔드 컨테이너와 모니터링 스택까지 함께 실행합니다.

```bash
scripts/local-infra.sh monitor
```

- API: http://localhost:8080/api/v1
- Grafana: http://localhost:3001
- Prometheus: http://localhost:9090
- Loki: http://localhost:3100

자세한 절차는 `docs/operations/runbook.md`를 참고합니다.

## 접속

기본 운영 서버에서는 모니터링 포트를 외부에 열지 않고 localhost에만 바인딩합니다.

```bash
ssh -i ~/.ssh/booktown-v2.pem \
  -L 3001:127.0.0.1:3001 \
  -L 9090:127.0.0.1:9090 \
  -L 9093:127.0.0.1:9093 \
  ubuntu@18.204.158.57
```

- Grafana: http://localhost:3001
- Prometheus: http://localhost:9090
- Alertmanager: http://localhost:9093

## 배포형 Grafana 접근

팀 접근이 필요하면 `grafana.booktown.shop`을 사용합니다.

필수 전제:

- Cloudflare DNS에 `grafana.booktown.shop` 추가
- Cloudflare Access 애플리케이션과 허용 사용자 정책 설정
- EC2 보안그룹은 80/443만 공개, Grafana 3001은 외부 공개 금지
- `/opt/booktown/.env`의 `GRAFANA_ROOT_URL=https://grafana.booktown.shop` 설정

EC2에서 Nginx route를 설치합니다.

```bash
cd /opt/booktown
CERTBOT_EMAIL=you@example.com sudo -E ./scripts/install-grafana-nginx.sh
```

설치 후 접속 주소:

- Grafana: https://grafana.booktown.shop

## 필수 환경변수

`/opt/booktown/.env`에 Grafana 관리자 계정을 추가합니다.

```env
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me
GRAFANA_ROOT_URL=https://grafana.booktown.shop
MYSQL_BACKUP_INTERVAL_SECONDS=86400
MYSQL_BACKUP_RETENTION_DAYS=7
```

SSH 포워딩으로만 Grafana를 볼 때는 `GRAFANA_ROOT_URL=http://localhost:3001`을 사용할 수 있지만, 배포형 접근을 적용하면 위 값처럼 운영 도메인으로 맞춥니다.

## 수집 대상

- Prometheus: `backend:8081/actuator/prometheus`
- Node Exporter: EC2 디스크/메모리 지표
- cAdvisor: Docker 컨테이너 메모리 지표
- Loki: Grafana Alloy가 Docker Compose 서비스 로그와 Nginx 로그 수집
- Alertmanager: Prometheus alert rule 수신

## 보관 정책

- Prometheus: 3일 또는 1GB
- Loki: 72시간

## Trace ID 로그 검색

백엔드는 모든 요청에 `X-Trace-Id`를 부여하고 JSON 로그의 `trace_id` 필드에 기록합니다.

Grafana Explore 또는 대시보드 로그 패널에서 다음처럼 검색합니다.

```logql
{service="backend"} | json | trace_id="TRACE_ID"
```

## 백업과 복구 리허설

`mysql-backup` 컨테이너는 `/opt/booktown/backups/mysql`에 gzip SQL dump를 생성합니다.

복구 리허설은 운영 DB를 덮어쓰지 않고 별도 DB에 복원합니다.

```bash
cd /opt/booktown
sudo MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" ./scripts/mysql-restore-rehearsal.sh ./backups/mysql/<dump-file>.sql.gz
```

## 알림 규칙

Prometheus rule은 다음을 감지합니다.

- Backend 5xx 증가
- 인증 실패 급증
- AI 호출 실패 또는 rate limit
- 디스크 여유 15% 미만
- 컨테이너 메모리 90% 초과
- MySQL backup 컨테이너 미관측
