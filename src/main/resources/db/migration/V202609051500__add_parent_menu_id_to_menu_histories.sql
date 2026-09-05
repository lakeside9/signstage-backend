-- 메뉴 관리 화면에서 상위 메뉴 이동(레벨 승격/편입)을 지원하기 위한 후속 — signstage-docs
-- business/menu-and-action-permission-management-review.md 7.1절, 12장 결정 #10 후속
-- (2026-09-05). 구조 변경 이력에 parent_menu_id를 추가해 "언제 어느 메뉴 밑으로 옮겨졌는지"도
-- 스냅샷에 남긴다. FK는 걸지 않는다 — 과거 스냅샷은 그 당시 상위 메뉴 id를 그대로 보존해야
-- 하고, 어차피 이 애플리케이션에서 menus 행은 삭제되지 않는다(비활성화만 한다).

ALTER TABLE menu_histories
    ADD COLUMN parent_menu_id BIGINT NULL AFTER menu_id;
