-- 앱 안 알림 — signstage-docs business/platform-order-partner-subcontract-review.md 12장(알림).
-- 받는 사람마다 한 줄을 만들고 읽음은 사람별로 기록한다. 메일·SMS 연동 전 1차 범위다.
CREATE TABLE notifications (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(50) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     VARCHAR(1000) NULL,
    link_path   VARCHAR(300) NULL,
    read_at     TIMESTAMP(6) NULL,
    created_by  BIGINT NULL,
    updated_by  BIGINT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notifications_user_read (user_id, read_at, id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기한 알림은 한 번만 보낸다.
ALTER TABLE partner_orders ADD COLUMN due_reminder_sent_at TIMESTAMP(6) NULL AFTER ceremony_id;
ALTER TABLE customer_invoices ADD COLUMN overdue_notified_at TIMESTAMP(6) NULL AFTER cancelled_at;
