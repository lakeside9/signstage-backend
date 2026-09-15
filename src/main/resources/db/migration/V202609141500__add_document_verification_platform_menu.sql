-- 플랫폼 관리자 사이드바에도 "진위여부 검증하기" 단축 링크 메뉴를 추가한다.
--
-- 사용자 요청(2026-09-14) — "진위여부 검증하기 메뉴를 플랫폼 관리자에도 추가를 해주세요."
-- 조직 축(ORGANIZATION)에는 이미 V202609101500에서 이 메뉴(MENU_ORG_VERIFY)를 추가했지만
-- PLATFORM 축에는 없었다 — 같은 화면(DocumentVerificationView)으로 가는 같은 성격의 단축
-- 링크를 PLATFORM 축에도 추가한다. 두 마이그레이션의 근거였던 "체크섬 대조라 로그인 없이
-- 열어도 안전하다"는 전제는 같은 날 뒤이은 변경(signstage-docs
-- business/ceremony-feature-migration-review.md §8.14)으로 이미 깨졌다 — `/verify`가
-- 로그인 필요한 화면(UserLayout 콘텐츠 영역)으로 바뀌었으므로, 플랫폼 관리자 계정으로도
-- 같은 화면에 들어갈 별도 진입점이 있어야 이 메뉴가 의미가 있다. 그래서 프런트(`App.tsx`)에
-- `AdminLayout` 하위 `/admin/verify` 경로로 같은 컴포넌트(`DocumentVerificationView`)를
-- 한 번 더 등록했다.
--
-- 기존 업무 그룹(MENU_GROUP_MEMBERS/BILLING/PURCHASE/DEMO/SYSTEM/SUPPORT) 중 어디에도
-- 속하지 않는 성격이라 — 데이터를 다루는 화면이 아니라 공개 검증 도구로 가는 통로일
-- 뿐이다 — 조직 축과 마찬가지로 그룹에 넣지 않고 최상위(parent_menu_id NULL) 단축
-- 링크로 둔다. "내 정보"(MENU_PROFILE, 현재 display_order 21) 바로 앞에 끼워 넣는다 —
-- V202609121100이 MENU_GROUP_SUPPORT를 추가할 때 썼던 것과 같은 "뒤 항목을 한 칸 밀고
-- 그 자리에 삽입" 방식이다.
UPDATE menus SET display_order = display_order + 1
WHERE console = 'PLATFORM' AND parent_menu_id IS NULL AND display_order >= 21;

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', NULL, 'MENU_PLATFORM_VERIFY', 'navigation.documentVerification', '/admin/verify', 'ShieldCheck', 21
FROM DUAL;

-- 권한 제한 없음(3개 역할 전부 true) — MENU_ORG_VERIFY와 같은 이유로, 역할별로 가릴 이유가
-- 없는 단순 화면 바로가기다.
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key = 'MENU_PLATFORM_VERIFY';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_PLATFORM_VERIFY' AND pd.permission_type = 'MENU';
