# Redis 성능 측정 환경 구현 요구사항

## 1. 프로젝트 개요

이 프로젝트는 Spring Boot와 Redis로 동영상 단건 조회의 Cache Stampede 방지 효과를 측정하는 로컬 성능 검증 환경입니다. 기능·제품 요구사항은 [PRD](PRD.md), 기술 설계는 [TRD](TRD.md)를 기준으로 하며, 이 문서는 AI 코딩 도구가 구현 시 따라야 할 현재 프로젝트 컨텍스트입니다.

## 2. 기술 스택

| 구분 | 기술 | 버전 또는 규칙 |
| --- | --- | --- |
| 언어 | Java | 26 Toolchain |
| 프레임워크 | Spring Boot | 4.1.1 |
| 빌드 | Gradle | `./gradlew` 사용 |
| 웹 | Spring MVC | REST Controller |
| 데이터 | Spring Data JPA, Hibernate, MySQL | MySQL 8.4 로컬 Compose |
| 캐시 | Spring Data Redis, Redis | Redis 7.4 로컬 Compose |
| 관측 | Actuator, Micrometer, Prometheus, Grafana | Prometheus endpoint 사용 |
| 테스트 | JUnit Jupiter, Mockito, AssertJ, H2 | H2 MySQL 호환 모드 |
| 부하 시험 | k6 | `k6/video-cache-stampede.js` |

스택을 변경할 때는 먼저 TRD를 수정하고 이 요약을 동기화합니다.

## 3. 구조와 책임

```text
src/main/java/dev/backend/redis_performance/
├── controller/             HTTP 요청과 응답 DTO 변환
├── controller/config/      API 예외 처리
├── service/                비즈니스 로직
├── service/cache/          Redis 캐시와 Cache Stampede 제어
├── service/dto/            서비스 계층 DTO
├── repository/             JPA 데이터 접근
├── domain/                 JPA 엔티티와 읽기 모델
└── dto/                    HTTP 요청·응답 DTO

src/test/java/dev/backend/redis_performance/
├── controller/             MockMvc 컨트롤러 테스트
└── service/                Mockito 기반 서비스 테스트
```

- HTTP 관련 작업은 `controller`와 `dto`에 둡니다.
- 비즈니스 규칙은 `service`에 둡니다.
- 데이터 접근은 `repository`에 둡니다.
- 새 애플리케이션 코드는 `dev.backend.redis_performance` 패키지 아래에 둡니다.
- 동영상 단건 조회용 캐시에는 JPA 엔티티가 아닌 `VideoDetail` 같은 읽기 모델을 저장합니다.

## 4. 현재 기능 계약

### API

| 메서드 | 경로 | 동작 |
| --- | --- | --- |
| POST | `/members` | 회원을 생성하고 HTTP 201을 반환합니다. |
| GET | `/members` | 회원 목록을 반환합니다. |
| GET | `/members/{id}` | 회원을 반환하거나 HTTP 404를 반환합니다. |
| POST | `/videos` | 회원이 존재할 때 동영상을 생성하고 HTTP 201을 반환합니다. |
| GET | `/videos` | 동영상 목록을 반환합니다. |
| GET | `/videos/{id}` | 캐시를 적용한 상세 정보를 반환하거나 HTTP 404를 반환합니다. |

### 캐시

- `VIDEO_CACHE_ENABLED`의 기본값은 `true`입니다.
- 캐시 키는 `video:detail:{id}`, Lock 키는 `video:detail:lock:{id}`입니다.
- 양수 캐시는 신선 30초와 stale 30초를 사용합니다.
- 존재하지 않는 동영상은 5초 동안 음수 캐시합니다.
- Lock은 UUID 토큰과 10초 TTL을 사용하고 Lua 스크립트로 소유자만 해제합니다.
- Redis 오류와 캐시 미스 Lock 대기 1초 초과는 DB 조회로 폴백합니다.
- stale 캐시는 즉시 반환하고, Lock을 획득한 한 요청만 비동기로 갱신합니다.
- `video.find.by.id.db.load` Counter는 실제 상세 DB 조회마다 증가합니다.

## 5. 코딩 규칙

- 기존 Java 스타일을 따릅니다. 탭 들여쓰기, 선언 줄의 여는 중괄호, 명시적 import를 사용합니다.
- 타입은 PascalCase, 메서드·변수·테스트 메서드는 camelCase를 사용합니다.
- Spring 의존성은 생성자 주입을 사용합니다.
- 새 Java 소스 파일의 첫 줄에는 파일 역할을 설명하는 한 줄 한국어 주석을 둡니다.
- 요청·응답 DTO와 서비스 DTO의 역할을 섞지 않습니다.
- 요청 데이터 유효성, API 오류 형식, 공개 API를 변경할 때는 TRD도 함께 갱신합니다.
- 설정값과 자격 증명은 환경 변수 또는 설정 파일을 사용하며 비밀값을 커밋하지 않습니다.

## 6. 작업 방식과 테스트

1. 구현 전 대상 코드와 호출부를 `rg`로 찾아 읽고, 변경 범위를 정합니다.
2. 비단순 작업은 `checklist.md`와 `context-notes.md`에 계획과 결정을 남깁니다.
3. 성공과 실패 경로를 모두 테스트합니다. 서비스는 Mockito, 웹 계층은 기존 MockMvc 패턴을 따릅니다.
4. 최소 관련 테스트를 실행한 뒤 제출 전 `./gradlew test`를 실행합니다.
5. 완료 보고에는 실제 실행한 검증 명령, 결과, 남은 위험을 기록합니다.

## 7. 금지 사항

- JPA 엔티티나 지연 로딩 프록시를 Redis에 직접 직렬화하지 않습니다.
- Redis 읽기·쓰기·Lock 실패 때문에 정상적인 DB 조회까지 실패시키지 않습니다.
- Lock을 토큰 검증 없이 해제하지 않습니다.
- `VIDEO_CACHE_ENABLED` 비교 경로를 제거하거나 무시하지 않습니다.
- 무관한 리팩터링, 기존 코드의 전면 포맷팅, 사용자 변경사항의 되돌리기를 하지 않습니다.
- 테스트 없는 동작 변경을 완료로 간주하지 않습니다.

## 8. 참고 문서

- [PRD](PRD.md). 무엇을, 왜 만드는지 정의합니다.
- [TRD](TRD.md). 시스템을 어떻게 설계하는지 정의합니다.
- [README](../README.md). 로컬 실행과 k6 실행 명령을 제공합니다.
- [AGENTS](../AGENTS.md). 저장소 작업 규칙을 정의합니다.
