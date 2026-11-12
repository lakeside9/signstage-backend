package com.eformworks.signstage.backend.feature.order.entity;

import java.math.BigDecimal;

/**
 * 감액 유형 — 파트너 외주 기본계약의 감액 기준표(signstage-docs
 * business/platform-order-partner-subcontract-review.md D13, 2026-11-12 결정). 검수 화면은 유형을 고르면
 * 발주 금액 × 기준 비율을 감액으로 제안한다. 검수자는 바꿀 수 있지만 사유를 남긴다. 계약서의 기준표와
 * 같아야 한다(하도급법상 계약 근거 없는 감액은 부당 감액이 될 수 있다).
 */
public enum InspectionDeductionType {
    LATE_START_MINOR("시작 지연 30분 미만", BigDecimal.ZERO),
    LATE_START_MAJOR("시작 지연 30분 이상", BigDecimal.valueOf(10)),
    PARTIAL_SIGNING_FAILURE("장비 하자로 일부 서명 불가", BigDecimal.valueOf(20)),
    EVENT_NOT_PERFORMED("행사 진행 불가", BigDecimal.valueOf(100)),
    /** 기준표에 없는 경우 — 제안 비율 없이 건별로 정한다. */
    OTHER("기타(건별 판단)", null);

    private final String label;
    private final BigDecimal ratePercent;

    InspectionDeductionType(String label, BigDecimal ratePercent) {
        this.label = label;
        this.ratePercent = ratePercent;
    }

    public String label() {
        return label;
    }

    /** 기준 감액 비율(발주 금액 대비 %). 기타는 null. */
    public BigDecimal ratePercent() {
        return ratePercent;
    }
}
