-- 단위 상품 추가구매 장바구니(서버 저장) — signstage-docs
-- business/unit-product-purchase-self-checkout-review.md 6장 결정(2026-09-11): "1차는
-- 프런트 상태만" 권장을 뒤집고 서버에 임시 저장한다. (ceremony_id, unit_product_id) 유니크
-- 제약으로 "같은 항목을 두 번 담으면 수량을 합친다"를 upsert 한 번으로 구현한다. 가격은
-- 담지 않는다 — 정가 스냅샷은 실제 구매 시점(ceremony_unit_product_purchase_lines)에만
-- 만들어진다.

CREATE TABLE ceremony_unit_product_cart_lines (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ceremony_id     BIGINT NOT NULL,
    unit_product_id BIGINT NOT NULL,
    quantity        INT NOT NULL,
    created_by      BIGINT NOT NULL,
    updated_by      BIGINT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_cart_line_ceremony_product UNIQUE (ceremony_id, unit_product_id),
    CONSTRAINT fk_cart_line_ceremony FOREIGN KEY (ceremony_id) REFERENCES ceremonies (id),
    CONSTRAINT fk_cart_line_unit_product FOREIGN KEY (unit_product_id) REFERENCES unit_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
