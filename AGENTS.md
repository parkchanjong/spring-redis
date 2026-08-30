# Repository Guidelines

## Project Structure and Architecture

This is a Gradle-based Spring Boot application for measuring Redis-backed application performance. Application code is under `src/main/java/dev/backend/redis_performance`; use the existing controller, service, repository, and domain layers. Runtime configuration is in `src/main/resources/application.properties`. Tests live in `src/test/java`, with the H2 test configuration in `src/test/resources/application.properties`.

Keep new code within the `dev.backend.redis_performance` package tree. Place HTTP concerns in controllers, business logic in services, and persistence access in repositories. Keep domain entities grouped by domain, for example `domain/member` and `domain/video`.

## Build, Test, and Local Development

- `docker compose up -d` starts local MySQL, Redis, Prometheus, and Grafana services.
- `./gradlew bootRun` starts the Spring Boot application on the local configuration.
- `./gradlew test` runs the JUnit 5 test suite using H2 in MySQL compatibility mode.
- `./gradlew build` compiles, tests, and packages the application.

Run the smallest relevant test while developing, then run `./gradlew test` before submitting a change. The project uses Java 26 through the Gradle toolchain.

## Coding Style and Naming

Follow the surrounding Java style: tab indentation, braces on the declaration line, and explicit imports grouped with Java imports first. Name types with PascalCase and methods, variables, and test methods with camelCase. Use Spring layer suffixes such as `MemberController`, `MemberService`, and `MemberRepository`.

Keep classes focused on one layer and use constructor injection for Spring dependencies. Add a short Korean comment at the top of newly created Java source files that explains the file's responsibility.

## Testing Guidelines

Tests use JUnit Jupiter, Mockito, and AssertJ. Name test classes after the subject under test, such as `MemberServiceTest`, and name test methods after the expected behavior, such as `throwsNotFoundWhenMemberDoesNotExist`. Unit-test services with Mockito; use the existing controller test patterns for web behavior. Cover successful and failure paths for changed behavior.

## Commits and Pull Requests

Recent commits use short Korean, imperative summaries, for example `Redis 성능 측정 환경 구성`. Keep each commit limited to one logical change. Pull requests should explain the behavioral change, link an issue when available, list the verification command and result, and include screenshots only for user-visible output.

## Configuration and Security

Use the environment variables documented in `README.md` for database, Redis, and Grafana credentials. Never commit secrets, local IDE files, Gradle caches, or generated `build/` output.
