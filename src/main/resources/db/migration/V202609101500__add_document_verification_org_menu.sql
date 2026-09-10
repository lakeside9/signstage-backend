-- 조직 사용자 사이드바에 "진위여부 검증하기" 메뉴를 추가한다.
--
-- legacy(~/Works/eform/source/signstage/signstage-frontend AdminLayout.tsx)가 이미 같은
-- 이름·아이콘(ShieldCheck)으로 사이드바에 노출해뒀던 항목이다 — 사용자 요청(2026-09-10,
-- "진위여부 검증하기 기능을 추가해주세요. 소스는 legacy를 참고하세요")으로 대조해보니, 이
-- 프로젝트는 결과 PDF 위변조 검증 기능(DocumentVerificationController, /api/verification/**,
-- 완전 공개 — signstage-docs business/ceremony-feature-migration-review.md §2.5) 자체는 이미
-- 구현돼 있었지만, 화면(DocumentVerificationView, `/verify`)이 어디서도 링크되지 않는 고아
-- 라우트였다 — URL을 직접 알아야만 들어갈 수 있었다.
--
-- legacy와 달리 이 프로젝트의 `/verify`는 로그인 여부와 무관하게 완전히 공개된 최상위 라우트로
-- 이미 설계돼 있다(체크섬 대조라 로그인 없이 열어도 안전하다는 기존 결정) — 그 설계는 그대로
-- 두고, 로그인한 조직 사용자가 사이드바에서 바로 찾을 수 있도록 단축 링크 메뉴만 추가한다.
-- 권한 제한 없음(4개 역할 전부 true) — 이 메뉴는 데이터를 다루는 액션이 아니라 공개 화면으로
-- 가는 단순 바로가기라, 다른 조직 메뉴처럼 역할별로 가릴 이유가 없다.

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'ORGANIZATION', NULL, 'MENU_ORG_VERIFY', 'navigation.documentVerification', '/verify', 'ShieldCheck', 3
FROM DUAL;

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'ORGANIZATION', id, label_key, display_order
FROM menus WHERE menu_key = 'MENU_ORG_VERIFY';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'MENU_ORG_VERIFY';
