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

## Video Cache Stampede 방지

`GET /videos/{id}`는 Redis에 동영상 조회 DTO를 JSON으로 저장합니다. JPA 엔티티를 직접 캐시하지 않아 지연 로딩 프록시를 직렬화하지 않습니다.

- 캐시 키는 `video:detail:{id}`이며, 신선한 값은 30초 동안 사용합니다.
- 신선 TTL이 지나도 30초 동안 stale 값을 즉시 반환합니다. 이때 `video:detail:lock:{id}` Lock을 획득한 요청 한 건만 DB를 비동기로 조회해 캐시를 갱신합니다.
- Lock은 UUID 토큰과 10초 TTL을 사용하며, Lua 스크립트로 Lock 소유자만 해제합니다.
- 존재하지 않는 영상은 5초 동안 음수 캐시합니다.
- Redis 오류나 최초 캐시 미스의 Lock 대기 1초 초과 시에는 DB로 조회를 대체합니다.

`VIDEO_CACHE_ENABLED`로 동일 애플리케이션에서 캐시 적용 전후를 비교할 수 있습니다. 기본값은 `true`입니다.

```bash
VIDEO_CACHE_ENABLED=false ./gradlew bootRun
VIDEO_CACHE_ENABLED=true ./gradlew bootRun
```

## k6 부하 테스트

Docker Compose와 애플리케이션을 실행한 뒤 [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/)가 설치된 환경에서 아래 명령을 실행합니다.

```bash
k6 run k6/video-cache-stampede.js
```

스크립트는 회원과 영상을 생성하고, 캐시를 채운 뒤 31초 대기해 stale 상태로 만든 다음 동일 영상에 200개 동시 요청을 보냅니다. 실행 마지막에는 Actuator의 `video_find_by_id_db_load_total` 증가량을 출력합니다.

동일한 로컬 환경에서 캐시 비활성·활성 실행을 각각 수행하고, k6 요약의 `http_req_duration` p95와 출력된 DB 조회 증가량을 아래 표에 기록합니다.

| 설정 | DB 조회 증가량 | p95 |
| --- | ---: | ---: |
| `VIDEO_CACHE_ENABLED=false` | 200 | 141.84ms |
| `VIDEO_CACHE_ENABLED=true` | 1 | 89.18ms |

캐시 활성화 시 정상 DB 응답 시간이 Lock 대기 시간 1초 이내라는 전제에서, 부하 구간의 DB 조회 증가량은 1이어야 합니다. p95는 머신과 실행 상태에 따라 달라지므로 고정된 통과 기준으로 사용하지 않고 실측값을 비교합니다.

위 결과는 2026-08-31 로컬 Docker 환경에서 측정했습니다.
