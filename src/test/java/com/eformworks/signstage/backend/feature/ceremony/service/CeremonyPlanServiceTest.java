package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.feature.billing.model.EffectiveDiscount;
import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyPlanService} 단위 테스트 — 플랜 확정 시 스냅샷 기록과 확정 조건.
 * 2026-11-11 {@code CeremonyServiceTest}에서 분리했다. 공용 mock과 서비스 조립은
 * {@link CeremonyServiceTestSupport}에 있다.
 */
class CeremonyPlanServiceTest extends CeremonyServiceTestSupport {

    /**
     * 2026-09-11 사용자 요청 — signstage-docs
     * business/ceremony-plan-price-snapshot-consistency-review.md 3.2절. "플랜 확정"이
     * 상태만 바꾸던 것에서, 확정 직전 오늘 날짜로 스냅샷을 한 번 더 찍도록 바뀌었다 —
     * 확정 버튼을 누르는 순간 보이던 값과 실제로 고정되는 값을 일치시킨다.
     */
    @Test
    @DisplayName("플랜 확정은 상태 전이 전에 오늘 날짜로 플랜 이력을 한 번 더 스냅샷한다")
    void confirmPlan_recordsFreshPlanHistorySnapshot() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        stubOwnerMember(ceremony);

        // 이 플랜은 포함 단위 상품이 없다고 가정한다 — createCeremony_withPlanDiscountOverride_snapshotsOverride와 같은 이유.
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of());
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, new BigDecimal("10000"))));
        given(organizationDiscountService.resolveBillingPlanDiscount(eq(organization), eq(101L), any(), any(), any(LocalDate.class)))
                .willReturn(new EffectiveDiscount(DiscountType.FIXED_AMOUNT, new BigDecimal("10000")));

        ceremonyPlanService.confirmPlan(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID);

        verify(ceremonyPlanHistoryRepository, org.mockito.Mockito.times(1)).save(any(CeremonyPlanHistory.class));
    }

    @Test
    @DisplayName("확정 — 플랜을 한 번도 선택하지 않았으면 거부한다")
    void confirmPlan_rejectsWhenNoPlanSelected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        stubOwnerMember(ceremony);

        assertThatThrownBy(() -> ceremonyPlanService.confirmPlan(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_PLAN_NOT_SELECTED);
    }
}
