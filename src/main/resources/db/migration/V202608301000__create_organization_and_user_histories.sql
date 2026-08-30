-- 파트너(조직) 정보와 회원 정보의 변경 이력 테이블. 카탈로그 *_histories(V202608212100)와 같은
-- 패턴 — 생성/수정 시점마다 그 순간의 전체 상태를 스냅샷으로 append-only 저장한다. 사용자
-- 본인이 바꾸든 플랫폼 관리자가 바꾸든 구분 없이 같은 테이블에 쌓인다(2026-08-30 요청).

CREATE TABLE organization_histories (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id   BIGINT NOT NULL,
    name              VARCHAR(100) NOT NULL,
    code              VARCHAR(50) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    default_locale    VARCHAR(10) NOT NULL,
    created_by        BIGINT NOT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_oh_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_oh_organization ON organization_histories (organization_id);

-- 이미 생성돼 있던 조직들도 현재 상태로 최초 이력 1건씩 백필한다(V202608212400과 같은 원칙) —
-- created_by는 그 조직 자체의 created_by를 그대로 물려받는다.
INSERT INTO organization_histories
    (organization_id, name, code, status, default_locale, created_by, created_at, updated_at)
SELECT id, name, code, status, default_locale, created_by, created_at, updated_at
FROM organizations;

-- user_histories.email/phone은 users 테이블과 마찬가지로 NULL 허용이다(V202608152000 —
-- 탈퇴 시 PII 마스킹으로 비워지기 때문). created_by도 users처럼 NULL 허용이다 — 셀프 회원가입은
-- 인증된 행위자가 아직 없는 시점에 만들어진다.
CREATE TABLE user_histories (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT NOT NULL,
    login_id                    VARCHAR(50) NOT NULL,
    name                        VARCHAR(100) NOT NULL,
    email                       VARCHAR(255) NULL,
    phone                       VARCHAR(20) NULL,
    locale                      VARCHAR(10) NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    platform_role               VARCHAR(20) NULL,
    is_password_reset_required  BOOLEAN NOT NULL,
    created_by                  BIGINT NULL,
    updated_by                  BIGINT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_uh_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_uh_user ON user_histories (user_id);

-- 이미 생성돼 있던 회원들도 현재 상태로 최초 이력 1건씩 백필한다 — 이미 탈퇴(WITHDRAWN)한
-- 회원이라도 지금 시점의 users 행 자체가 이미 마스킹된 상태라 그대로 백필해도 PII가 새로
-- 드러나지 않는다.
INSERT INTO user_histories
    (user_id, login_id, name, email, phone, locale, status, platform_role, is_password_reset_required, created_by, created_at, updated_at)
SELECT id, login_id, name, email, phone, locale, status, platform_role, is_password_reset_required, created_by, created_at, updated_at
FROM users;
