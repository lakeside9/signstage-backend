package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.billing.entity.TaxCategory;
import com.eformworks.signstage.backend.feature.billing.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyQuoteService} 단위 테스트 — 예상 청구액의 통화 반올림과 세금 정책 적용.
 * 2026-11-11 {@code CeremonyServiceTest}에서 분리했다. 공용 mock과 서비스 조립은
 * {@link CeremonyServiceTestSupport}에 있다.
 */
class CeremonyQuoteServiceTest extends CeremonyServiceTestSupport {

    @Test
    @DisplayName("KRW 예상 청구액은 통화 0자리 반올림 후 유효 세금 정책의 VAT를 합산한다")
    void calculateEstimatedTotal_appliesCurrencyRoundingAndTaxPolicy() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("기본").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();
        TaxPolicy taxPolicy = mock(TaxPolicy.class);

        UnitProduct signers = unitProduct(901L, UnitProductType.SIGNERS);
        BillingPlanUnitProduct line = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(signers).includedQuantity(1).build();

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        // ceremony가 DRAFT라 findLatestPlanHistoryForSnapshot이 스냅샷 조회 자체를 건너뛰고
        // 항상 라이브 값으로 폴백한다(2026-09-10) — ceremonyPlanHistoryRepository는 안 불린다.
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(line));
        given(unitProductPricePeriodRepository.findEffective(eq(901L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(signers, new BigDecimal("10000"), new BigDecimal("10005"))));
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, BigDecimal.ZERO)));
        given(ceremonyUnitProductPurchaseLineRepository.findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(10L)).willReturn(List.of());
        given(taxPolicyResolver.resolve(any(), any(), any())).willReturn(taxPolicy);
        given(taxPolicy.getRatePercent()).willReturn(new BigDecimal("10.0000"));
        given(taxPolicy.getCategory()).willReturn(TaxCategory.STANDARD);
        given(taxPolicy.getPriceInclusion()).willReturn("EXCLUSIVE");

        CeremonyDto.Response.EstimatedTotal result =
                ceremonyQuoteService.calculateEstimatedTotal(ORGANIZATION_ID, 10L, CURRENT_USER_ID);

        assertThat(result.getNetAmount()).isEqualByComparingTo("10005");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("1001");
        assertThat(result.getGrossAmount()).isEqualByComparingTo("11006");
        assertThat(result.getCurrencyCode()).isEqualTo("KRW");
        assertThat(result.getFractionDigits()).isZero();
    }
}
