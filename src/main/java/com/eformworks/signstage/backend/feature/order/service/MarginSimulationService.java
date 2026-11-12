package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.order.dto.MarginSimulationDto;
import com.eformworks.signstage.backend.feature.order.dto.PartnerEvaluationDto;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminSalesOrderDto;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCalculation;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영업 마진 시뮬레이션 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 8단계.
 * 견적 입력(플랜·지역·옵션·할인)으로 고객 금액을 계산하고, 기본 단가표와 발주 가능한 파트너별 단가표로
 * 발주 금액·마진을 나란히 보여준다. 수주 견적·발주와 같은 계산을 쓰며 아무것도 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarginSimulationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BillingCatalogQueryService billingCatalogQueryService;
    private final OrderPricingService orderPricingService;
    private final PartnerEvaluationService partnerEvaluationService;

    public MarginSimulationDto.Response.Simulation simulate(MarginSimulationDto.Request.Simulate request) {
        LocalDate today = LocalDate.now(SalesOrderService.PLATFORM_ZONE);
        String currencyCode = InternationalizationDefaults.CURRENCY_CODE;
        CurrencyPolicy currencyPolicy = new CurrencyPolicy(
                currencyCode, Currency.getInstance(currencyCode).getDefaultFractionDigits(), RoundingMode.HALF_UP
        );
        BillingPlan plan = billingCatalogQueryService.findBillingPlanOrThrow(request.getBillingPlanId());
        List<SalesQuoteOption> options = request.getOptions() == null
                ? List.of()
                : request.getOptions().stream()
                        .map(option -> new SalesQuoteOption(option.getUnitProductId(), option.getQuantity()))
                        .toList();
        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                plan, options, request.getRegion(), request.scopeExtras(), request.getDiscountAmount(), currencyPolicy, today
        );

        MarginSimulationDto.Response.PartnerQuote defaultQuote =
                quote(null, null, null, calculation, request.getRegion(), BigDecimal.ZERO, currencyPolicy, today);
        List<MarginSimulationDto.Response.PartnerQuote> partnerQuotes = partnerEvaluationService.findEvaluations().stream()
                .filter(PartnerEvaluationDto.Response.PartnerEvaluation::isOrderEligible)
                .map(evaluation -> quote(
                        evaluation.getOrganizationId(), evaluation.getOrganizationName(), evaluation,
                        calculation, request.getRegion(), request.getAdjustmentAmount(), currencyPolicy, today
                ))
                .sorted(Comparator.comparing(
                        MarginSimulationDto.Response.PartnerQuote::getPartnerAmount,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();

        return new MarginSimulationDto.Response.Simulation(
                currencyCode,
                calculation.lines().stream().map(PlatformAdminSalesOrderDto.Response.QuoteLine::from).toList(),
                calculation.supplyAmount(),
                calculation.vatAmount(),
                calculation.supplyAmount().add(calculation.vatAmount()),
                calculation.tabletCount(),
                calculation.onsiteStaffPersonDays(),
                orderPricingService.minimumMarginRate().multiply(HUNDRED).stripTrailingZeros(),
                defaultQuote,
                partnerQuotes
        );
    }

    /**
     * organizationId가 null이면 기본 단가표로, 조정액 없이 계산한다 — 견적 저장 때 검사하는 예상 발주
     * 금액과 같다. 적용할 단가표가 없으면 금액 없이 돌려준다.
     */
    private MarginSimulationDto.Response.PartnerQuote quote(
            Long organizationId,
            String organizationName,
            PartnerEvaluationDto.Response.PartnerEvaluation evaluation,
            SalesQuoteCalculation calculation,
            ServiceRegion region,
            BigDecimal adjustmentAmount,
            CurrencyPolicy currencyPolicy,
            LocalDate today
    ) {
        boolean partnerRateCardApplied = organizationId != null && orderPricingService.hasPartnerRateCard(organizationId, today);
        BigDecimal partnerAmount;
        try {
            partnerAmount = orderPricingService.calculatePartnerOrderAmount(
                    organizationId, calculation.tabletCount(), calculation.onsiteStaffPersonDays(), calculation.extras(), region,
                    adjustmentAmount, currencyPolicy, today
            ).totalAmount();
        } catch (ApplicationException ex) {
            if (ex.getErrorCode() != OrderErrorCode.PARTNER_RATE_CARD_NOT_FOUND) {
                throw ex;
            }
            return new MarginSimulationDto.Response.PartnerQuote(
                    organizationId, organizationName, false, null, null, null, null, evaluation
            );
        }
        BigDecimal supplyAmount = calculation.supplyAmount();
        BigDecimal marginAmount = supplyAmount.subtract(partnerAmount);
        BigDecimal marginRatePercent = supplyAmount.signum() == 0
                ? null
                : marginAmount.multiply(HUNDRED).divide(supplyAmount, 1, RoundingMode.HALF_UP);
        return new MarginSimulationDto.Response.PartnerQuote(
                organizationId,
                organizationName,
                partnerRateCardApplied,
                partnerAmount,
                marginAmount,
                marginRatePercent,
                orderPricingService.isPartnerOrderMarginSufficient(supplyAmount, partnerAmount),
                evaluation
        );
    }
}
