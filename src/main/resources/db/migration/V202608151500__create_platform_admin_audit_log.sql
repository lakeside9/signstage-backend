-- platform_admin_audit_log 최초 생성.
--
-- 설계 원안은 signstage-docs business/user-organization-design.md 3.2절 항목 5이며,
-- organization_id를 NOT NULL + FK로 뒀다("플랫폼 관리자가 조직 스코핑을 우회해 조직 데이터에
-- 접근할 때"를 전제한 설계). 하지만 실제로 구현된 관리자 제어 기능(회원 상태 변경/잠금 해제/
-- 강제 비밀번호 재설정/회원 생성/관리자 계정 생성·해제/조직 상태 변경) 중 다수가 특정 조직과
-- 무관해 원안 그대로 쓸 수 없었다. 그래서 이번 마이그레이션은 아래처럼 조정했다:
--   - organization_id를 NULL 허용으로 완화(조직과 무관한 행위는 NULL)
--   - 대상이 사용자인 행위를 기록할 target_user_id 추가
--   - admin_user_id/organization_id의 FK를 생략(login_history와 같은 이유 —
--     signstage-docs business/login-security.md 3.4절, database/audit-columns.md 2장 "예외 2".
--     쓰기 빈도가 높은 append-only 로그 테이블이라 FK 인덱스의 락 경합·마이그레이션 순서
--     제약을 피한다)
-- 상세 근거는 signstage-docs backend/platform-admin-console-implementation.md 참고.

CREATE TABLE platform_admin_audit_log (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_user_id    BIGINT NOT NULL,        -- 행위자(이 로그 자체가 "누가 했는지" 기록)
    action           VARCHAR(100) NOT NULL,  -- 예: UPDATE_USER_STATUS, CREATE_ACCOUNT
    target_user_id   BIGINT NULL,            -- 행위 대상이 사용자인 경우
    organization_id  BIGINT NULL,            -- 행위 대상/범위가 조직인 경우
    detail           VARCHAR(500) NULL,      -- 예: "status: PENDING -> ACTIVE"
    request_path     VARCHAR(255) NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_paal_admin_user_id ON platform_admin_audit_log (admin_user_id);
CREATE INDEX idx_paal_target_user_id ON platform_admin_audit_log (target_user_id);
CREATE INDEX idx_paal_organization_id ON platform_admin_audit_log (organization_id);
CREATE INDEX idx_paal_created_at ON platform_admin_audit_log (created_at);
