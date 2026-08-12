# Redis Refresh Token 병렬 통합 테스트

## 목적

같은 Refresh Token으로 정상적인 병렬 갱신 요청이 들어올 때 한 요청만 토큰을 교체하고, grace window 안의 나머지 요청은 교체된 현재 토큰을 동일하게 반환하는지 확인합니다.

## 측정 조건

| 항목 | 값 |
|---|---|
| 실행 일자 | 2026-08-13 |
| EC2 검증 시각 | 2026-08-13 08:21:38 KST |
| EC2 환경 | AWS EC2 `t3.small`, Docker의 Gradle 9.4.1/JDK 21 |
| Redis | `redis:7.4-alpine` |
| Java | 21 |
| Gradle | 9.4.1 |
| 병렬 요청 | 32 |
| grace window | 15초 |
| 대상 | `RefreshTokenService.rotate`의 Lua 스크립트 |
| 테스트 | `RefreshTokenServiceRedisIntegrationTest` |

## 실행 명령

```powershell
docker run --rm -d --name booktown-redis-evidence -p 16379:6379 redis:7.4-alpine
$env:BOOKTOWN_REDIS_INTEGRATION='true'
$env:BOOKTOWN_REDIS_HOST='127.0.0.1'
$env:BOOKTOWN_REDIS_PORT='16379'
.\gradlew.bat cleanTest test --tests com.booktown.domain.auth.service.RefreshTokenServiceRedisIntegrationTest --no-daemon
docker stop booktown-redis-evidence
```

한글이 포함된 Windows 경로에서 Gradle 테스트 워커가 클래스를 읽지 못하면 저장소를 영문 경로에 매핑한 뒤 같은 명령을 실행합니다.

## 결과

- Gradle 결과: `BUILD SUCCESSFUL`
- 토큰 교체: 1건
- 현재 토큰 동일 반환: 31건
- 실패: 0건

실제 Redis 7.4에서 32개 요청이 모두 같은 현재 토큰을 받았고 Redis에도 그 토큰 하나만 저장됐습니다.

## 해석과 한계

- 이 테스트는 단일 Redis 인스턴스의 Lua 원자성과 애플리케이션 결과 코드 매핑을 검증합니다.
- Redis Cluster, 네트워크 단절, failover 중 재시도까지 검증한 결과는 아닙니다.
- 15초가 지난 이전 토큰 재사용은 세션을 폐기해 탈취 재사용으로 처리합니다.
- grace window 안에서 발생한 실제 탈취 요청은 정상 경합과 구분할 수 없습니다.
- 성능 지표가 아니라 동시 요청의 중복 성공 방지에 대한 기능 검증입니다.
