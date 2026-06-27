# SonarCloud 설정

BookTown Backend는 EC2에 SonarQube를 직접 올리지 않고 SonarCloud를 사용한다.

## GitHub 설정

Repository Settings에서 다음 값을 추가한다.

### Secrets

- `SONAR_TOKEN`: SonarCloud project token

### Variables

- `SONAR_PROJECT_KEY`: SonarCloud project key
- `SONAR_ORGANIZATION`: SonarCloud organization key

## 실행 조건

- PR과 `dev`/`master` push에서 CI 이후 SonarCloud job이 실행된다.
- 위 Secret/Variable이 없으면 job은 안내 메시지만 출력하고 통과한다.

## 초기 Quality Gate 기준

초기에는 coverage를 강하게 막기보다 다음 항목 중심으로 시작한다.

- Bugs
- Vulnerabilities
- Security Hotspots
- Code Smells
- Duplications on new code

Coverage 기준은 테스트 기반이 안정된 뒤 단계적으로 강화한다.
