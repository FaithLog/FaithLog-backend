# FaithLog Backend

FaithLog 교회/캠퍼스 운영 앱의 Spring Boot 백엔드입니다.

## Tech Stack

- Java 21
- Spring Boot
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL
- Redis
- Firebase FCM
- Gradle
- Docker Compose
- Swagger/OpenAPI

## Local Infrastructure

```bash
docker compose up -d postgres redis
```

Spring Boot 프로젝트가 추가된 뒤에는 애플리케이션 이미지까지 함께 실행할 수 있습니다.

```bash
./gradlew build
docker compose up --build
```

## Architecture

FaithLog는 MSA가 아닌 하나의 Spring Boot 애플리케이션 안에서 도메인 경계를 나누는 모듈러 모놀리스 구조를 사용합니다.

- 도메인별 패키지 분리
- `domain`, `application`, `infrastructure`, `presentation` 계층 사용
- Request DTO가 application service로 직접 들어가지 않도록 Command 사용
- Controller에서 Entity 직접 반환 금지
- PostgreSQL은 Spring Boot auto-configuration 사용
- Redis 설정은 `global/config/RedisConfig.java`, 실제 Redis 구현체는 각 도메인의 `infrastructure/redis`에 배치
