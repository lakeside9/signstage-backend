package com.eformworks.signstage.backend.feature.billing.model;

import com.eformworks.signstage.backend.core.money.DiscountType;
import java.math.BigDecimal;

/**
 * 조직×플랜 할인 오버라이드를 반영한 실제 적용 할인값. {@code OrganizationDiscountService}가 해석하고
 * {@code CeremonyPlanService}가 플랜 스냅샷 컬럼에 그대로 옮겨 담는다.
 */
public record EffectiveDiscount(DiscountType type, BigDecimal value) {
}
