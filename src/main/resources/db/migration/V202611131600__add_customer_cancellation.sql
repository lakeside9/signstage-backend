-- 고객 취소 처리(2026-11-13, signstage-docs business/platform-order-pending-items.md C01~C04 권장안)와 발주 패키지 구성 복사(I05).

-- C01: 행사 취소 상태. status는 VARCHAR라 값만 늘린다(CANCELLED).
ALTER TABLE ceremonies ADD COLUMN cancelled_at TIMESTAMP(6) NULL AFTER completed_at;

-- C02·C03: 고객 사정 취소와 적용한 취소 수수료.
ALTER TABLE sales_orders
    ADD COLUMN cancelled_by_customer         BOOLEAN NOT NULL DEFAULT FALSE AFTER cancellation_reason,
    ADD COLUMN cancellation_fee_rate_percent DECIMAL(7,4) NULL AFTER cancelled_by_customer,
    ADD COLUMN cancellation_fee_amount       DECIMAL(19,4) NULL AFTER cancellation_fee_rate_percent;

-- C03: 받은 금액이 취소 수수료보다 많을 때의 환불. 환불은 시스템 밖에서 하고 기록만 한다.
CREATE TABLE customer_refunds (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sales_order_id   BIGINT NOT NULL,
    customer_id      BIGINT NOT NULL,
    currency_code    CHAR(3) NOT NULL,
    amount           DECIMAL(19,4) NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    refunded_date    DATE NULL,
    refund_memo      VARCHAR(500) NULL,
    refunded_by      BIGINT NULL,
    recorded_at      TIMESTAMP(6) NULL,
    created_by       BIGINT NULL,
    updated_by       BIGINT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_refunds_sales_order (sales_order_id),
    CONSTRAINT fk_customer_refund_sales_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_customer_refund_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_refund_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- C04: 수락한 파트너의 고객 취소 보상. 다음 월 정산 지급 건에 더한다.
CREATE TABLE partner_compensations (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id    BIGINT NOT NULL,
    partner_order_id   BIGINT NOT NULL,
    currency_code      CHAR(3) NOT NULL,
    amount             DECIMAL(19,4) NOT NULL,
    rate_percent       DECIMAL(7,4) NOT NULL,
    reason             VARCHAR(500) NOT NULL,
    occurred_on        DATE NOT NULL,
    status             VARCHAR(20) NOT NULL,
    partner_payout_id  BIGINT NULL,
    created_by         BIGINT NULL,
    updated_by         BIGINT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_partner_compensations_pending (status, occurred_on),
    CONSTRAINT fk_partner_compensation_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_partner_compensation_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders (id),
    CONSTRAINT fk_partner_compensation_payout FOREIGN KEY (partner_payout_id) REFERENCES partner_payouts (id),
    CONSTRAINT fk_partner_compensation_currency FOREIGN KEY (currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- I05: 발주 시점의 패키지 기본 포함 구성 복사본. 이 전에 낸 발주는 복사본이 없어 지금 플랜 구성으로 보인다.
CREATE TABLE partner_order_package_items (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_order_id  BIGINT NOT NULL,
    name              VARCHAR(200) NOT NULL,
    quantity          INT NOT NULL,
    quantity_unit     VARCHAR(20) NOT NULL,
    display_order     INT NOT NULL,
    created_by        BIGINT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_partner_order_package_items_order (partner_order_id, display_order),
    CONSTRAINT fk_partner_order_package_item_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 이미 취소된 수주 정리: 고객 수락 뒤 취소된 수주는 고객 취소로 보고(수수료 기록 없음), 그 발주에 연결된 행사 중
-- 완료되지 않은 것을 취소 상태로 닫는다(파트너 교체 뒤 넘겨받기를 기다리던 행사 포함).
UPDATE sales_orders
SET cancelled_by_customer = TRUE
WHERE status = 'CANCELLED' AND accepted_at IS NOT NULL;

UPDATE ceremonies c
JOIN (
    SELECT DISTINCT po.ceremony_id
    FROM partner_orders po
    JOIN sales_orders so ON so.id = po.sales_order_id
    WHERE so.status = 'CANCELLED' AND po.ceremony_id IS NOT NULL
) closed ON closed.ceremony_id = c.id
SET c.status = 'CANCELLED',
    c.cancelled_at = CURRENT_TIMESTAMP(6),
    c.partner_access_suspended_at = COALESCE(c.partner_access_suspended_at, CURRENT_TIMESTAMP(6))
WHERE c.status <> 'COMPLETED';
