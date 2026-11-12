package com.eformworks.signstage.backend.feature.order.model;

import java.math.BigDecimal;

/**
 * 견적에 넣는 직접 입력 항목 — 카탈로그에 없는 품목. 고객 단가로 견적 줄을 만들고, 발주 단가(없으면 0)에
 * 수량을 곱해 발주 금액에 더한다.
 */
public record SalesQuoteCustomItem(String name, int quantity, BigDecimal unitPrice, BigDecimal partnerUnitPrice) {
}
