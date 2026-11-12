package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.order.dto.MarginSimulationDto;
import com.eformworks.signstage.backend.feature.order.dto.PartnerEvaluationDto;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderAmount;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCalculation;
import com.eformworks.signstage.backend.feature.order.model.ScopeExtras;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link MarginSimulationService} 단위 테스트 — 기본·파트너별 발주 금액과 마진(11장 8단계). */
@ExtendWith(MockitoExtension.class)
class MarginSimulationServiceTest {

    @Mock
    private BillingCatalogQueryService billingCatalogQueryService;
    @Mock
    private OrderPricingService orderPricingService;
    @Mock
    private PartnerEvaluationService partnerEvaluationService;

    @InjectMocks
    private MarginSimulationService marginSimulationService;

    @Test
    @DisplayName("발주 가능한 파트너만 금액 오름차순으로 계산하고, 단가표가 없는 파트너는 금액 없이 둔다")
    void simulate_quotesEligiblePartners() {
        BillingPlan plan = OrderFixtures.plan(1L);
        given(billingCatalogQueryService.findBillingPlanOrThrow(1L, SalesChannel.PLATFORM_ORDER)).willReturn(plan);
        given(orderPricingService.calculateSalesQuote(eq(plan), any(), eq(ServiceRegion.CAPITAL), any(), any(), any(), any()))
                .willReturn(new SalesQuoteCalculation(List.of(), new BigDecimal("1000000"), new BigDecimal("100000"), 10, 2, ScopeExtras.NONE, null, 0));
        given(orderPricingService.minimumMarginRate()).willReturn(new BigDecimal("0.20"));
        given(orderPricingService.isPartnerOrderMarginSufficient(any(), any())).willAnswer(invocation ->
                ((BigDecimal) invocation.getArgument(1)).compareTo(new BigDecimal("800000")) <= 0);
        given(partnerEvaluationService.findEvaluations()).willReturn(List.of(
                evaluation(7L, true), evaluation(8L, true), evaluation(9L, true), evaluation(10L, false)
        ));
        given(orderPricingService.hasPartnerRateCard(any(), any())).willAnswer(invocation ->
                invocation.getArgument(0).equals(8L));
        given(orderPricingService.calculatePartnerOrderAmount(isNull(), anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .willReturn(amount(700000));
        given(orderPricingService.calculatePartnerOrderAmount(eq(7L), anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .willReturn(amount(700000));
        given(orderPricingService.calculatePartnerOrderAmount(eq(8L), anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .willReturn(amount(850000));
        given(orderPricingService.calculatePartnerOrderAmount(eq(9L), anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .willThrow(new ApplicationException(OrderErrorCode.PARTNER_RATE_CARD_NOT_FOUND));

        MarginSimulationDto.Response.Simulation simulation = marginSimulationService.simulate(
                new MarginSimulationDto.Request.Simulate(1L, ServiceRegion.CAPITAL, List.of(), null, null, null, null, null, null)
        );

        assertThat(simulation.getTotalAmount()).isEqualByComparingTo("1100000");
        assertThat(simulation.getMinimumMarginRatePercent()).isEqualByComparingTo("20");
        assertThat(simulation.getDefaultQuote().getMarginAmount()).isEqualByComparingTo("300000");
        assertThat(simulation.getDefaultQuote().getMarginRatePercent()).isEqualByComparingTo("30.0");
        assertThat(simulation.getDefaultQuote().getEvaluation()).isNull();

        assertThat(simulation.getPartnerQuotes()).extracting(MarginSimulationDto.Response.PartnerQuote::getOrganizationId)
                .containsExactly(7L, 8L, 9L);
        MarginSimulationDto.Response.PartnerQuote special = simulation.getPartnerQuotes().get(1);
        assertThat(special.isPartnerRateCardApplied()).isTrue();
        assertThat(special.getMarginRatePercent()).isEqualByComparingTo("15.0");
        assertThat(special.getMarginSufficient()).isFalse();
        MarginSimulationDto.Response.PartnerQuote missing = simulation.getPartnerQuotes().get(2);
        assertThat(missing.getPartnerAmount()).isNull();
        assertThat(missing.getMarginSufficient()).isNull();
        assertThat(missing.getEvaluation().getOrganizationId()).isEqualTo(9L);
    }

    private PartnerEvaluationDto.Response.PartnerEvaluation evaluation(Long organizationId, boolean eligible) {
        return PartnerEvaluationDto.Response.PartnerEvaluation.of(organizationId, "파트너" + organizationId, eligible, null, null);
    }

    private PartnerOrderAmount amount(long total) {
        BigDecimal zero = BigDecimal.ZERO;
        return new PartnerOrderAmount(zero, 10, zero, 2, zero, zero, zero, BigDecimal.valueOf(total), 0, 0, 0, zero, null, null, 0, zero);
    }
}
