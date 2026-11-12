package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.OrganizationSubscription;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOrderRoute;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.model.QuoteCalculation;
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

    @Test
    @DisplayName("구독으로 확정한 행사의 예상 이용료는 플랜 금액을 다시 세지 않는다(D21)")
    void buildQuoteCalculation_excludesPlanForSubscriptionCeremony() {
        BillingPlan plan = BillingPlan.builder().name("구독").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization()).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);
        ReflectionTestUtils.setField(ceremony, "subscription", mock(OrganizationSubscription.class));
        given(ceremonyUnitProductPurchaseLineRepository.findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(10L)).willReturn(List.of());

        QuoteCalculation calculation = ceremonyQuoteService.buildQuoteCalculation(ceremony);

        assertThat(calculation.planAppliedPrice()).isEqualByComparingTo("0");
        assertThat(calculation.netAmount()).isEqualByComparingTo("0");
        assertThat(calculation.lines()).isEmpty();
    }

    @Test
    @DisplayName("구독 행사의 고객 견적 원가는 구독 1회분 중 플랫폼 이용료 비중이다(D21)")
    void calculateSubscriptionUsageCost_usesOneUseShare() {
        BillingPlan plan = BillingPlan.builder().name("구독").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization()).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);
        OrganizationSubscription subscription = mock(OrganizationSubscription.class);
        given(subscription.getPurchaseAmountSnapshot()).willReturn(new BigDecimal("100000"));
        given(subscription.getAllowedCountSnapshot()).willReturn(5);
        ReflectionTestUtils.setField(ceremony, "subscription", subscription);

        UnitProduct signers = unitProduct(901L, UnitProductType.SIGNERS);
        UnitProduct tablets = UnitProduct.builder()
                .type(UnitProductType.SIGNERS).name("태블릿").category(UnitProductCategory.EQUIPMENT).build();
        ReflectionTestUtils.setField(tablets, "id", 902L);
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(
                BillingPlanUnitProduct.builder().billingPlan(plan).unitProduct(signers).includedQuantity(1).build(),
                BillingPlanUnitProduct.builder().billingPlan(plan).unitProduct(tablets).includedQuantity(1).build()
        ));
        given(unitProductPricePeriodRepository.findEffective(eq(901L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(signers, new BigDecimal("10000"), new BigDecimal("10000"))));
        given(unitProductPricePeriodRepository.findEffective(eq(902L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(tablets, new BigDecimal("10000"), new BigDecimal("10000"))));
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, BigDecimal.ZERO)));

        // 1회분 100,000 ÷ 5 = 20,000 중 플랫폼 이용료(서명자) 비중 1/2
        assertThat(ceremonyQuoteService.calculateSubscriptionUsageCost(ceremony)).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("구독 행사가 아니면 구독 원가는 0이다")
    void calculateSubscriptionUsageCost_zeroWithoutSubscription() {
        Ceremony ceremony = Ceremony.builder().organization(organization()).title("행사").build();

        assertThat(ceremonyQuoteService.calculateSubscriptionUsageCost(ceremony)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("플랫폼 수주 행사는 예상 이용료를 조회할 수 없다")
    void calculateEstimatedTotal_rejectsPlatformOrderCeremony() {
        Ceremony ceremony = Ceremony.builder().organization(organization()).orderRoute(CeremonyOrderRoute.PLATFORM_ORDER).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);
        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(Member.builder().role(MemberRole.OWNER).build()));

        assertThatThrownBy(() -> ceremonyQuoteService.calculateEstimatedTotal(ORGANIZATION_ID, 10L, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(CeremonyErrorCode.CEREMONY_PLATFORM_ORDER_RESTRICTED);
    }

    @Test
    @DisplayName("파트너 교체로 접근이 막힌 행사는 조직 회원에게 없는 행사로 보인다(D03·D18)")
    void findCeremony_hidesPartnerAccessSuspendedCeremony() {
        Ceremony ceremony = Ceremony.builder().organization(organization()).orderRoute(CeremonyOrderRoute.PLATFORM_ORDER).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);
        ceremony.suspendPartnerAccess();
        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));

        assertThatThrownBy(() -> ceremonyQuoteService.calculateEstimatedTotal(ORGANIZATION_ID, 10L, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(CeremonyErrorCode.CEREMONY_NOT_FOUND);

        ceremony.transferTo(organization());
        assertThat(ceremony.isPartnerAccessSuspended()).isFalse();
    }
}
