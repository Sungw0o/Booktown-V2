# Redis Refresh Token 병렬 통합 테스트

## 목적

Mockito로 Redis 응답을 흉내 내는 단위 테스트와 실제 Redis Lua 원자 실행을 구분합니다. 같은 Refresh Token으로 여러 갱신 요청이 동시에 들어올 때 성공 요청이 하나로 제한되는지 확인합니다.

## 측정 조건

| 항목 | 값 |
|---|---|
| 실행 일자 | 2026-08-13 |
| Redis | `redis:7.4-alpine` |
| Java | 21 |
| Gradle | 9.4.1 |
| 병렬 요청 | 32 |
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
- 성공: 1건
- 거절: 31건
- 허용된 거절 코드: `REFRESH_TOKEN_REUSED`, `REFRESH_TOKEN_NOT_FOUND`

실제 Redis에 대해 Lua 스크립트가 원자적으로 실행되고 동일한 기존 토큰으로 두 번 이상 교체되지 않음을 확인했습니다.

## 해석과 한계

- 이 테스트는 단일 Redis 인스턴스의 Lua 원자성과 애플리케이션 결과 코드 매핑을 검증합니다.
- Redis Cluster, 네트워크 단절, failover 중 재시도까지 검증한 결과는 아닙니다.
- 같은 탭의 401 요청은 프론트엔드 단일 실행 큐로 합치지만, 다중 탭이나 네트워크 재시도로 이전 토큰이 다시 전달되면 현재 정책은 Redis 세션을 폐기합니다. 정상 경합과 탈취를 구분하는 grace window 또는 토큰 회전 결과 재사용은 후속 과제입니다.
- 성능 지표가 아니라 동시 요청의 중복 성공 방지에 대한 기능 검증입니다.
