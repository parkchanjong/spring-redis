# redis-performance

로컬 Spring 애플리케이션과 Docker 기반 MySQL, Redis, Prometheus, Grafana 환경입니다.

## 시작하기

```bash
docker compose up -d
./gradlew bootRun
```

기본 접속 주소입니다.

- MySQL은 `localhost:3306`입니다.
- Redis는 `localhost:6379`입니다.
- Prometheus는 `http://localhost:9090`입니다.
- Grafana는 `http://localhost:3000`입니다.
- Actuator Prometheus 지표는 `http://localhost:8080/actuator/prometheus`입니다.

Grafana 기본 계정은 `admin`과 `admin`이며, 처음 시작하면 Prometheus 데이터 소스와 Redis Performance 대시보드가 자동으로 등록됩니다.

## 환경 변수

기본값은 로컬 개발용입니다. `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `MYSQL_EXPORTER_USER`, `MYSQL_EXPORTER_PASSWORD`, `REDIS_PASSWORD`, `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`로 Compose 값을 변경할 수 있습니다.

Spring 애플리케이션은 `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`를 사용합니다.

MySQL 초기화 계정 정보를 변경하려면 기존 named volume을 제거한 뒤 다시 시작해야 합니다.
