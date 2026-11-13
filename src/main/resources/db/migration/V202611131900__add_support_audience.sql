-- FAQ·공지사항 대상 구분(2026-11-13): 파트너용(로그인한 조직 콘솔)과 고객용(로그인 없이 보는 공개 화면).
-- 지금까지의 글은 모두 조직 콘솔에 보이던 것이라 파트너용으로 둔다.
ALTER TABLE faqs ADD COLUMN audience VARCHAR(20) NOT NULL DEFAULT 'PARTNER' AFTER id;
ALTER TABLE announcements ADD COLUMN audience VARCHAR(20) NOT NULL DEFAULT 'PARTNER' AFTER id;

CREATE INDEX idx_faqs_audience_active_order ON faqs (audience, is_active, display_order);
CREATE INDEX idx_announcements_audience_active ON announcements (audience, is_active, is_pinned, created_at);

-- 공개 홈에 고정 문구로 있던 고객용 자주 묻는 질문을 옮긴다. 등록자는 가장 먼저 만든 최고 관리자로 둔다
-- (관리자가 없는 새 DB에서는 넣지 않는다 — 관리자 화면에서 등록하면 된다).
INSERT INTO faqs (audience, category, question, answer, display_order, is_active, created_by)
SELECT 'CUSTOMER', NULL, q.question, q.answer, q.display_order, TRUE, admin.id
FROM (
    SELECT '어떤 행사에 쓸 수 있나요?' AS question,
           '협약식·업무협약(MOU)·조인식·서명식처럼 여러 서명자가 한자리에서 문서에 서명하는 행사에 쓸 수 있습니다.' AS answer,
           10 AS display_order
    UNION ALL SELECT '서명자가 앱을 설치해야 하나요?',
           '아니요. 행사용 태블릿이나 서명자에게 안내된 QR코드로 브라우저에서 바로 서명합니다.', 20
    UNION ALL SELECT '본 행사 전에 연습할 수 있나요?',
           '테스트·리허설 행사를 만들어 본 행사와 같은 흐름으로 미리 진행할 수 있고, 연습 서명은 초기화할 수 있습니다.', 30
    UNION ALL SELECT '장비나 현장 인력도 지원되나요?',
           '현장 지원을 신청하면 태블릿·프로젝터 장비와 운영 인력을 함께 지원합니다. 범위는 행사 규모에 따라 견적으로 안내합니다.', 40
    UNION ALL SELECT '요금은 어떻게 정해지나요?',
           '행사 단위 이용과 기관 구독 중에서 고를 수 있고, 서명자·태블릿 수와 현장 지원 범위에 따라 견적을 드립니다.', 50
    UNION ALL SELECT '서명이 끝난 문서는 어떻게 받나요?',
           '서명이 끝나면 결과 문서가 만들어져 행사 화면에서 내려받을 수 있고, 배포한 문서는 문서 검증으로 원본 여부를 확인할 수 있습니다.', 60
) q
JOIN (
    SELECT id FROM users WHERE platform_role = 'PLATFORM_SUPER' ORDER BY id LIMIT 1
) admin;
