# TRD: Redis 성능 측정 환경

## 문서 정보

| 항목 | 내용 |
| --- | --- |
| 버전 | 1.0 |
| 작성일 | 2026-09-01 |
| 상태 | Approved |
| 관련 문서 | [PRD](PRD.md) |

---

## 1. 기술 개요

### 1.1 시스템 아키텍처

```text
                         k6
                          |
                          | HTTP
                          v
                Spring Boot 애플리케이션
                ┌─────────────────────────┐
                │ Controller              │
                │ Service                 │
                │ VideoCacheService       │
                │ Spring Data JPA / Redis │
                │ Actuator                │
                └───────┬─────────┬───────┘
                        |         |
                 JDBC   |         | Redis protocol
                        v         v
                    MySQL       Redis
                        |         |
              mysqld-exporter  redis-exporter
                        \         /
                         v       v
                       Prometheus
                           |
                           v
                        Grafana
```

### 1.2 기술 스택

| 레이어 | 기술 | 버전 또는 구성 |
| --- | --- | --- |
| 언어 | Java | 26 Toolchain |
| 프레임워크 | Spring Boot | 4.1.1 |
| 빌드 | Gradle | Wrapper 사용 |
| 웹·운영 | Spring MVC, Actuator | Prometheus endpoint 노출 |
| 영속성 | Spring Data JPA, Hibernate | MySQL 8.4 |
| 캐시 | Spring Data Redis | Redis 7.4 Alpine |
| 직렬화 | Jackson | `VideoDetail` JSON 캐시 |
| 관측 | Micrometer, Prometheus, Grafana | Prometheus 3.5.0, Grafana 11.6.0 |
| 부하 시험 | k6 | 동시 요청 시나리오 |
| 테스트 | JUnit Jupiter, Mockito, AssertJ, H2 | H2 MySQL 호환 모드 |

---

## 2. 컴포넌트와 데이터 흐름

### 2.1 애플리케이션 계층

| 계층 | 책임 |
| --- | --- |
| Controller | 회원·동영상 HTTP 요청과 응답 DTO 변환을 처리합니다. |
| Service | 회원·동영상 생성 및 일반 조회를 처리합니다. |
| VideoCacheService | 동영상 단건 조회의 Redis 캐시, Lock, 비동기 갱신, DB 폴백을 처리합니다. |
| Repository | JPA 엔티티 저장과 동영상 상세 읽기 모델 조회를 처리합니다. |
| Domain | JPA 엔티티와 캐시 가능한 `VideoDetail` 읽기 모델을 정의합니다. |

### 2.2 `GET /videos/{id}` 흐름

1. `VideoController`가 식별자를 `VideoService.findById`에 전달합니다.
2. 캐시가 비활성화된 경우 `VideoCacheService`가 즉시 MySQL의 `VideoDetail` 조회로 진행합니다.
3. 캐시가 활성화된 경우 `video:detail:{id}`를 읽습니다.
4. 신선한 양수 캐시가 있으면 즉시 반환하고, 음수 캐시가 있으면 404를 반환합니다.
5. stale 양수 캐시가 있으면 즉시 반환하고, `video:detail:lock:{id}` Lock 획득에 성공한 요청만 비동기 갱신을 실행합니다.
6. 캐시 미스에서는 Lock 획득 요청이 MySQL을 조회해 캐시를 채웁니다. Lock 획득 실패 요청은 최대 1초 동안 캐시를 재확인합니다.
7. 대기 중 캐시가 채워지지 않거나 Redis 접근에 실패하면 MySQL 조회로 폴백합니다.
8. MySQL 상세 조회마다 `video.find.by.id.db.load` Counter를 증가시킵니다.

### 2.3 캐시 정책

| 항목 | 값 | 동작 |
| --- | --- | --- |
| 캐시 키 | `video:detail:{id}` | `VideoDetail`을 포함하는 JSON을 저장합니다. |
| 신선 TTL | 30초 | 신선한 양수 캐시를 즉시 반환합니다. |
| stale TTL | 30초 | 만료 후에도 즉시 반환하고 비동기 갱신을 시도합니다. |
| 양수 캐시 저장 TTL | 60초 | 신선 TTL과 stale TTL의 합입니다. |
| 음수 캐시 TTL | 5초 | 존재하지 않는 동영상 결과를 저장합니다. |
| Lock 키 | `video:detail:lock:{id}` | 캐시 채움과 stale 갱신을 한 요청으로 제한합니다. |
| Lock TTL | 10초 | Lock 해제 실패 시에도 자동 만료됩니다. |
| Lock 대기 | 최대 1초, 10ms 간격 | 미획득 요청이 채워진 캐시를 기다립니다. |

Lock 값에는 UUID 토큰을 저장합니다. 해제는 Lua 스크립트로 토큰이 같은 소유자만 키를 삭제하도록 보장합니다.

---

## 3. 데이터 모델

```text
Member (members) 1 ───── * Video (videos)

members
├── id          BIGINT PK, identity
├── name        NOT NULL
└── created_at  NOT NULL, 생성 시각

videos
├── id          BIGINT PK, identity
├── member_id   BIGINT FK -> members.id, NOT NULL
├── title       NOT NULL
├── description nullable
├── view_count  NOT NULL, 기본값 0
├── like_count  NOT NULL, 기본값 0
└── created_at  NOT NULL, 생성 시각
```

`VideoRepository.findDetailById`는 `Video`와 작성자 `Member`를 join하여 다음 읽기 모델을 생성합니다. 이 모델만 Redis에 저장해 JPA 지연 로딩 프록시 직렬화를 피합니다.

```java
record VideoDetail(
    Long id,
    Long memberId,
    String title,
    String description,
    long viewCount,
    long likeCount,
    LocalDateTime createdAt
) {}
```

---

## 4. API 설계

### 4.1 공통 규칙

- Base URL은 `/`입니다.
- 요청과 성공 응답은 JSON을 사용합니다.
- 생성 성공은 HTTP 201을 반환합니다.
- 존재하지 않는 회원 또는 동영상은 빈 본문의 HTTP 404를 반환합니다.

### 4.2 회원 API

| 메서드 | 경로 | 설명 | 성공 응답 |
| --- | --- | --- | --- |
| POST | `/members` | 회원 생성 | 201, `MemberResponse` |
| GET | `/members` | 회원 목록 조회 | 200, `MemberResponse[]` |
| GET | `/members/{id}` | 회원 단건 조회 | 200, `MemberResponse` |

```json
POST /members
{
  "name": "chan"
}

201 Created
{
  "id": 1,
  "name": "chan",
  "createdAt": "2026-09-01T13:00:00"
}
```

### 4.3 동영상 API

| 메서드 | 경로 | 설명 | 성공 응답 |
| --- | --- | --- | --- |
| POST | `/videos` | 회원이 작성한 동영상 생성 | 201, `VideoResponse` |
| GET | `/videos` | 동영상 목록 조회 | 200, `VideoResponse[]` |
| GET | `/videos/{id}` | 캐시가 적용되는 동영상 단건 조회 | 200, `VideoResponse` |

```json
POST /videos
{
  "memberId": 1,
  "title": "redis",
  "description": "performance test"
}

201 Created
{
  "id": 1,
  "memberId": 1,
  "title": "redis",
  "description": "performance test",
  "viewCount": 0,
  "likeCount": 0,
  "createdAt": "2026-09-01T13:00:00"
}
```

---

## 5. 관측, 인프라와 실행

### 5.1 지표와 대시보드

- Spring Actuator는 `/actuator/prometheus`에서 Prometheus 형식 지표를 노출합니다.
- `video.find.by.id.db.load`는 동영상 상세 DB 조회 횟수를 계수합니다.
- Prometheus는 15초 간격으로 Spring 애플리케이션, MySQL exporter, Redis exporter를 수집합니다.
- Grafana 대시보드는 HTTP 요청률, Redis 메모리와 연결 수, MySQL 연결 수와 상태, Redis 상태를 표시합니다.

### 5.2 로컬 실행

```bash
docker compose up -d
./gradlew bootRun
```

| 서비스 | 기본 주소 |
| --- | --- |
| MySQL | `localhost:3306` |
| Redis | `localhost:6379` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` |
| Actuator | `http://localhost:8080/actuator/prometheus` |

MySQL, Redis, Grafana 비밀번호는 Compose와 애플리케이션 환경 변수로 주입합니다. 기본값은 로컬 개발 전용입니다.

### 5.3 부하 시험

```bash
K6_WEB_DASHBOARD=true k6 run k6/video-cache-stampede.js
```

실행 중에는 `http://localhost:5665/ui/?endpoint=/`에서 요청 수, 응답 시간, 체크 성공률을 확인합니다. 대시보드는 k6 프로세스가 실행되는 동안에만 제공됩니다.

시나리오는 HTTP API로 회원과 동영상을 자동 생성한 뒤 해당 동영상의 캐시를 채우고, 31초를 기다려 stale 상태로 만듭니다. 이후 200개 동시 요청을 한 번씩 실행하며 종료 시 Actuator에서 DB 조회 Counter의 증가량을 출력합니다. 실행마다 테스트 회원과 동영상 데이터가 추가됩니다.

Grafana 관측용 지속 부하에는 아래 시나리오를 사용합니다.

```bash
k6 run k6/video-cache-sustained.js
```

이 시나리오는 캐시를 채운 같은 동영상에 초당 50회씩 60초간 요청합니다. Prometheus는 Spring 애플리케이션을 1초마다 수집하며, Grafana는 영상 GET 요청률, DB 조회율, 선택 시간 범위의 DB 조회 증가량을 표시합니다. 캐시 활성·비활성 실행 결과는 이 세 지표로 비교합니다.

---

## 6. 테스트 전략

| 범위 | 검증 대상 |
| --- | --- |
| 서비스 단위 테스트 | 캐시 히트·미스, Lock 대기, stale 갱신, 음수 캐시, Redis 장애 폴백, 캐시 비활성화입니다. |
| 서비스 단위 테스트 | 회원·동영상 생성과 조회, 존재하지 않는 리소스 처리입니다. |
| 컨트롤러 테스트 | 각 API의 성공 응답과 404 응답입니다. |
| 애플리케이션 테스트 | Spring Boot 컨텍스트 기동입니다. |
| 부하 시험 | Cache Stampede 구간의 DB 조회 증가량과 p95 비교입니다. |

코드 변경 후에는 최소 관련 테스트를 먼저 실행하고, 제출 전 `./gradlew test`를 실행합니다.
