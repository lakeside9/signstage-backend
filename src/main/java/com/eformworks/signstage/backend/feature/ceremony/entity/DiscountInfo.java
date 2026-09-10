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
 * 할인 표현(discountType/discountValue) 값 객체. 옛 카탈로그 가격정보({@code CatalogPriceInfo},
 * 지금은 {@link BillingPlanDiscountPeriod}만 할인을 갖는 구조로 바뀌며 삭제됨, signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 2026-09-10)와 조직×품목 할인
 * 오버라이드({@code OrganizationBillingPlanDiscount} 등 3종 + 그 이력 3종)가 전부 이 쌍을
 * 독립적으로 갖고 있던 걸 하나로 모았다 — signstage-docs
 * business/billing-catalog-zero-base-schema-redesign-review.md 결정 #4(2026-09-08).
 *
 * <p>조직×품목 오버라이드 쪽은 카탈로그보다 좁은 정밀도(precision 12, scale 2)를 써왔으므로
 * 그 필드는 {@code @AttributeOverride}로 유지한다 — 이 클래스 자체의 기본 정밀도는 카탈로그
 * 쪽(19, 4)에 맞춘다.
 *
 * <p>할인값 하한·상한 검증을 이 생성자 한 곳에서 강제한다 — signstage-docs
 * business/organization-discount-override-security-and-validity-period-review.md 결정
 * #1(2026-09-08). 음수 할인값은 계산상 "할증"이 되고(값을 만드는 모든 경로가 이 생성자를
 * 거치므로 카탈로그/조직 오버라이드/행사 건별 재량 할인 세 곳을 한 번에 막는다), 정률(PERCENT)
 * 할인은 100을 넘으면 의미가 없다. Hibernate가 DB에서 읽어올 때는 {@link #DiscountInfo()}
 * (protected no-args) + 필드 접근을 쓰므로 이 생성자를 거치지 않는다 — 새로 쓰는 값만 검증한다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscountInfo {

    private static final BigDecimal PERCENT_MAX = BigDecimal.valueOf(100);

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    public DiscountInfo(DiscountType discountType, BigDecimal discountValue) {
        if (discountType == null) {
            throw new IllegalArgumentException("discountType is required");
        }
        if (discountValue == null) {
            throw new IllegalArgumentException("discountValue is required");
        }
        if (discountValue.signum() < 0) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_VALUE_INVALID);
        }
        if (discountType == DiscountType.PERCENT && discountValue.compareTo(PERCENT_MAX) > 0) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_VALUE_INVALID);
        }
        this.discountType = discountType;
        this.discountValue = discountValue;
    }
}
