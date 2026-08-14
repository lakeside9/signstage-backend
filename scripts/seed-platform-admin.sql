-- 플랫폼 관리자(PLATFORM_SUPER) 최초 계정 시딩 스크립트
--
-- 1회성 수동 실행 전용이다. Flyway 마이그레이션(db/migration)에 포함하지 않는다 —
-- 포함하면 dev/stage/prd에서도 자동으로 실행되어 버려, 배포 시점의 계정 생성을
-- 사람이 통제한다는 원칙(business/user-organization-design.md 7.2절)이 깨진다.
--
-- 사용 순서:
--   1. ./gradlew hashPassword --console=plain 로 초기 비밀번호의 BCrypt 해시를 생성한다.
--   2. 아래 <PLACEHOLDER> 값을 실제 값으로 바꾼다.
--   3. 대상 환경 DB에 이 스크립트를 직접 실행한다.
--        예) mysql -h <host> -P <port> -u <user> -p <database> < scripts/seed-platform-admin.sql
--   4. 발급한 초기 비밀번호로 최초 로그인하면 비밀번호 변경이 강제된다
--      (is_password_reset_required = TRUE, business/user-organization-design.md 5.3절).
--
-- users 테이블 스키마는 business/user-organization-design.md 3.2절을 따른다.
-- 이 스크립트를 실행하기 전에 해당 스키마의 마이그레이션이 먼저 적용되어 있어야 한다.

INSERT INTO users (
    login_id,
    name,
    email,
    password,
    status,
    platform_role,
    is_password_reset_required,
    created_by,
    updated_by
) VALUES (
    '<LOGIN_ID>',            -- 예: platform-admin
    '<NAME>',                -- 예: 플랫폼 운영자
    '<EMAIL>',               -- 예: ops@eformworks.com
    '<BCRYPT_HASH>',         -- 1번 단계에서 생성한 해시 (평문 비밀번호를 여기 적지 않는다)
    'ACTIVE',
    'PLATFORM_SUPER',
    TRUE,
    NULL,                    -- 최초 계정이라 생성 주체(created_by)가 없다
    NULL
);
