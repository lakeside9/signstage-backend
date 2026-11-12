package com.eformworks.signstage.backend.feature.order.model;

/** 견적에 넣는 추가 옵션(단위 상품과 수량). */
public record SalesQuoteOption(Long unitProductId, int quantity) {
}
