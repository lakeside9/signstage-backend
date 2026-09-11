package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파트너가 실고객에게 재판매할 때 시스템 사용료 원가 위에 얹는 마진 — signstage-docs
 * business/partner-customer-quote-design-review.md 5장 결정(2026-09-11), 같은 문서가 요청한
 * 대로 {@link DiscountInfo}와 같은 모양(type/value, 하한 0 이상 검증)으로 만들되 타입은
 * 재사용({@link DiscountType} — PERCENT/FIXED_AMOUNT는 "할인"뿐 아니라 "가산"에도 그대로
 * 맞는 표현이라 새 enum을 따로 두지 않는다). {@link DiscountInfo}와 다른 점은 정률(PERCENT)에
 * 상한이 없다는 것 — 마진은 원가의 몇 배를 얹어도(예: 300%) 사업적으로 유효하다.
 *
 * <p>이 값은 플랫폼이 통제하지 않는다(business/platform-partner-customer-billing-model-reference.md
 * 4장 결정 — 파트너 OWNER가 전적으로 자유롭게 정한다). "설정되지 않음"은 이 클래스가 아니라
 * 호출부(조직 기본값/행사별 override 컬럼이 둘 다 null)로 표현한다 — 이 클래스 자체는 항상
 * 구체적인 값만 담는다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarginInfo {

    @Enumerated(EnumType.STRING)
    @Column(name = "margin_type", length = 20)
    private DiscountType marginType;

    @Column(name = "margin_value", precision = 19, scale = 4)
    private BigDecimal marginValue;

    public MarginInfo(DiscountType marginType, BigDecimal marginValue) {
        if (marginType == null) {
            throw new IllegalArgumentException("marginType is required");
        }
        if (marginValue == null) {
            throw new IllegalArgumentException("marginValue is required");
        }
        if (marginValue.signum() < 0) {
            throw new ApplicationException(CeremonyErrorCode.MARGIN_VALUE_INVALID);
        }
        this.marginType = marginType;
        this.marginValue = marginValue;
    }
}
