-- "구독 플랜 카탈로그" 메뉴 비활성화 — signstage-docs
-- business/platform-admin-partner-ux-confusion-review.md 3.1절 "안 B" 채택(2026-09-14).
-- "과금 플랜"(`MENU_BILLING_PLANS`)과 "구독 플랜 카탈로그"(`MENU_SUBSCRIPTION_PLANS`)가
-- 같은 `BillingPlan` 목록을 서로 다른 진입점 2개로 쪼개 놓았던 것을 관리자 콘솔
-- `AdminBillingPlanList` 화면 하나로 합쳤다(유형 필터 추가) — 프런트가
-- `/admin/billing-catalog/subscription-plans`를 통합 화면으로 리다이렉트하므로 옛 경로로
-- 들어와도 화면 자체는 계속 동작한다. 메뉴 행은 지우지 않고 비활성화만 한다(기존 관례,
-- V202609101100 등과 동일 — "메뉴 자체를 새로 만들거나 삭제할 수 없다, 배포로만
-- 등록/변경한다"). 이력은 menu_histories에 자연스럽게 남는다. "구독 요청 관리"
-- (`MENU_SUBSCRIPTION_REQUESTS`)는 카탈로그 열람이 아니라 신청 승인/반려 워크플로라
-- 대상이 아니다 — 건드리지 않는다.
UPDATE menus SET active = FALSE WHERE menu_key = 'MENU_SUBSCRIPTION_PLANS';
UPDATE permission_definitions SET active = FALSE
    WHERE permission_key = 'MENU_SUBSCRIPTION_PLANS' AND permission_type = 'MENU';
