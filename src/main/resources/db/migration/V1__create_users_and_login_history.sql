-- users, login_history 최초 생성.
-- 스키마는 signstage-docs business/user-organization-design.md 3.2절,
-- business/login-security.md 3.2/4.1절을 그대로 따른다.
-- (조직 관련 테이블은 이번 최소 구현 범위 밖이라 아직 만들지 않는다.)

CREATE TABLE users (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    login_id                    VARCHAR(50) NOT NULL UNIQUE,
    name                        VARCHAR(100) NOT NULL,
    email                       VARCHAR(255) NOT NULL UNIQUE,
    phone                       VARCHAR(20) NULL,
    locale                      VARCHAR(10) NOT NULL DEFAULT 'ko-KR',
    password                    VARCHAR(255) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    platform_role               VARCHAR(20) NULL,
    is_password_reset_required  BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_count          INT NOT NULL DEFAULT 0,
    locked_until                TIMESTAMP NULL,
    created_by                  BIGINT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE login_history (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NULL,
    login_id_input VARCHAR(50) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    ip_address     VARCHAR(45) NOT NULL,
    user_agent     VARCHAR(500) NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_login_history_user_id ON login_history (user_id);
CREATE INDEX idx_login_history_login_id_input ON login_history (login_id_input);
CREATE INDEX idx_login_history_created_at ON login_history (created_at);
