# signstage-backend
싸인스테이지 백엔드 (SpringBoot)

코딩 규칙과 설계·결정 문서는 별도 저장소 `signstage-docs`에 있다. 코드를 고치기 전에
`signstage-docs/backend/backend-coding-convention.md`를 먼저 읽는다.

## 실행 환경

- Java 25 (Gradle toolchain), Spring Boot 4.1, Gradle Wrapper(`./gradlew`)
- MySQL 8.0 — 스키마는 Flyway(`src/main/resources/db/migration`)가 기동 시 적용하고,
  JPA는 `ddl-auto: validate`로 검증만 한다.
- 기본 프로필은 `local`(`application.yaml`)이며 설정은 `application-local.yaml`에 있다.

## 로컬 실행

```bash
./gradlew bootRun
```

`application-local.yaml`의 값은 환경변수로 덮어쓸 수 있다.

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| `SERVER_PORT` | `8050` | 서버 포트 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3308` / `new_signstage` | MySQL 접속 |
| `DB_USERNAME` / `DB_PASSWORD` | `application-local.yaml` 참고 | MySQL 계정 |
| `STORAGE_LOCAL_BASE_DIR` | `./data/uploads` | 업로드 파일 저장 위치(커밋 제외) |
| `JWT_SECRET` | 로컬 전용 기본값 | JWT 서명 키. dev/stage/prd에서는 반드시 지정 |

- Swagger UI: `http://localhost:8050/swagger-ui.html` (API 문서: `/api-docs`)
- 로그: 콘솔과 `./logs/signstage-backend.log`(커밋 제외)

## 테스트

```bash
./gradlew test
```

`BackendApplicationTests#contextLoads`는 실제 애플리케이션 컨텍스트를 띄우므로 로컬 MySQL이
떠 있어야 한다. 나머지 단위 테스트는 Mockito 기반이라 DB가 필요 없다.

## 최초 계정과 시드 데이터

플랫폼 관리자 계정은 Flyway에 넣지 않고 수동으로 만든다(배포 시점 계정 생성을 사람이 통제).

1. BCrypt 해시 생성: `./gradlew hashPassword --console=plain` (표준입력) 또는
   `./gradlew hashPassword --args="평문비밀번호"`
2. `scripts/seed-platform-admin.sql`에 해시를 넣고 실행한다.

| 스크립트 | 용도 |
| --- | --- |
| `scripts/seed-platform-admin.sql` | 최초 플랫폼 관리자(PLATFORM_SUPER) 계정 생성, 1회성 |
| `scripts/seed-onsite-online-support-unit-products.sql` | 현장지원·온라인지원 단위 상품 등록 |
| `scripts/preflight-ceremony-event-effects.sql` | 이벤트 효과 마이그레이션 적용 전 읽기 전용 점검 |
| `scripts/verify-ceremony-event-effects-migration.sql` | 이벤트 효과 마이그레이션 적용 후 읽기 전용 검증 |
| `scripts/backups/` | 마이그레이션 전 백업 SQL |

## 패키지 구조

```text
com.eformworks.signstage.backend
├── core          # 공통 기술 기반(config, error, i18n, jpa, logging, money, security, tool, web)
├── feature       # 업무별 기능(audit, billing, ceremony, demo, identity, organization, permission, support)
└── integration   # 외부 연동 구현체(storage)
```

자세한 배치 규칙은 `backend-coding-convention.md` 3장을 따른다.
