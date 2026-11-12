package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.service.TaxPolicyResolver;
import com.eformworks.signstage.backend.feature.order.entity.CustomerPrice;
import com.eformworks.signstage.backend.feature.order.entity.PartnerRateCard;
import com.eformworks.signstage.backend.feature.order.entity.RegionSurcharge;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLineType;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderAmount;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCalculation;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteOption;
import com.eformworks.signstage.backend.feature.order.repository.CustomerPriceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerRateCardRepository;
import com.eformworks.signstage.backend.feature.order.repository.RegionSurchargeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객 견적과 발주 금액을 계산한다 — signstage-docs
 * business/platform-order-partner-subcontract-review.md 6장(범위 플랜 + 고객 가격표 + 파트너 단가표).
 *
 * <ul>
 *   <li>고객 견적: 패키지(플랜) 가격 + 옵션(단위 상품) 가격 × 수량 + 지역 할증 − 영업 할인. 줄마다
 *       세금 별도 부가세를 계산한다({@code CeremonyQuoteService}와 같은 세율 조회).</li>
 *   <li>범위 수량: 태블릿·현장 인력은 플랜 포함 수량과 옵션 수량을 더한다. 현장 인력
 *       ({@code ONSITE_SUPPORT}) 수량은 인·일로 본다.</li>
 *   <li>발주 금액: 파트너 예외 단가표가 있으면 그것을, 없으면 기본 단가표를 쓴다(D05·D28 안 A).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderPricingService {

    private static final String TAX_COUNTRY_CODE = "KR";

    private final CustomerPriceRepository customerPriceRepository;
    private final RegionSurchargeRepository regionSurchargeRepository;
    private final PartnerRateCardRepository partnerRateCardRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final TaxPolicyResolver taxPolicyResolver;
    private final MoneyCalculator moneyCalculator;
    private final PlatformOrderMarginPolicy platformOrderMarginPolicy;

    public SalesQuoteCalculation calculateSalesQuote(
            BillingPlan plan,
            List<SalesQuoteOption> options,
            ServiceRegion region,
            BigDecimal discountAmount,
            CurrencyPolicy currencyPolicy,
            LocalDate asOfDate
    ) {
        List<SalesQuoteCalculation.Line> lines = new ArrayList<>();
        List<BillingPlanUnitProduct> planUnitProducts = billingCatalogQueryService.findPlanUnitProducts(plan.getId());
        int tabletCount = sumIncluded(planUnitProducts, UnitProductType.TABLETS);
        int onsiteStaffPersonDays = sumIncluded(planUnitProducts, UnitProductType.ONSITE_SUPPORT);

        CustomerPrice planPrice = customerPriceRepository.findEffectiveForPlan(plan.getId(), asOfDate)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.CUSTOMER_PRICE_NOT_FOUND));
        checkCurrency(planPrice.getCurrencyCode(), currencyPolicy);
        lines.add(buildLine(SalesOrderLineType.BILLING_PLAN, null, plan.getName(), 1,
                planPrice.getUnitPrice(), planPrice.getTaxCode(), currencyPolicy, asOfDate));

        Set<Long> seenUnitProductIds = new HashSet<>();
        for (SalesQuoteOption option : options == null ? List.<SalesQuoteOption>of() : options) {
            if (!seenUnitProductIds.add(option.unitProductId())) {
                throw new ApplicationException(OrderErrorCode.OPTION_DUPLICATED);
            }
            UnitProduct unitProduct = billingCatalogQueryService.findUnitProductOrThrow(option.unitProductId());
            int quantity = unitProduct.getType().isToggle() ? 1 : option.quantity();
            if (quantity < 1) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
            CustomerPrice optionPrice = customerPriceRepository.findEffectiveForUnitProduct(unitProduct.getId(), asOfDate)
                    .orElseThrow(() -> new ApplicationException(OrderErrorCode.CUSTOMER_PRICE_NOT_FOUND));
            checkCurrency(optionPrice.getCurrencyCode(), currencyPolicy);
            lines.add(buildLine(SalesOrderLineType.UNIT_PRODUCT, unitProduct, unitProduct.getName(), quantity,
                    optionPrice.getUnitPrice(), optionPrice.getTaxCode(), currencyPolicy, asOfDate));
            if (unitProduct.getType() == UnitProductType.TABLETS) {
                tabletCount += quantity;
            } else if (unitProduct.getType() == UnitProductType.ONSITE_SUPPORT) {
                onsiteStaffPersonDays += quantity;
            }
        }

        RegionSurcharge surcharge = findRegionSurchargeOrThrow(region, asOfDate);
        checkCurrency(surcharge.getCurrencyCode(), currencyPolicy);
        if (surcharge.getCustomerAmount().signum() > 0) {
            lines.add(buildLine(SalesOrderLineType.REGION_SURCHARGE, null, "지역 할증(" + region.label() + ")", 1,
                    surcharge.getCustomerAmount(), surcharge.getTaxCode(), currencyPolicy, asOfDate));
        }

        BigDecimal subtotal = lines.stream().map(SalesQuoteCalculation.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : moneyCalculator.normalize(discountAmount, currencyPolicy);
        if (discount.signum() < 0 || discount.compareTo(subtotal) > 0) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        if (discount.signum() > 0) {
            // 할인은 패키지 세율로 음수 줄을 둔다 — 줄별 세액 합계가 그대로 부가세가 되게 한다.
            SalesQuoteCalculation.Line discountLine = buildLine(SalesOrderLineType.DISCOUNT, null, "영업 할인", 1,
                    discount, planPrice.getTaxCode(), currencyPolicy, asOfDate);
            lines.add(new SalesQuoteCalculation.Line(
                    SalesOrderLineType.DISCOUNT, null, discountLine.name(), 1,
                    discountLine.unitPrice().negate(), discountLine.amount().negate(),
                    discountLine.taxCode(), discountLine.taxRatePercent(), discountLine.taxAmount().negate()
            ));
        }

        BigDecimal supplyAmount = lines.stream().map(SalesQuoteCalculation.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vatAmount = lines.stream().map(SalesQuoteCalculation.Line::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalesQuoteCalculation(lines, supplyAmount, vatAmount, tabletCount, onsiteStaffPersonDays);
    }

    /**
     * @param organizationId 발주 대상 파트너. null이면 기본 단가표로 계산한다(견적 시점 예상 발주 금액).
     */
    public PartnerOrderAmount calculatePartnerOrderAmount(
            Long organizationId,
            int tabletCount,
            int onsiteStaffPersonDays,
            ServiceRegion region,
            BigDecimal adjustmentAmount,
            CurrencyPolicy currencyPolicy,
            LocalDate asOfDate
    ) {
        PartnerRateCard rateCard = (organizationId == null
                ? partnerRateCardRepository.findEffectiveDefault(asOfDate)
                : partnerRateCardRepository.findEffectiveForPartner(organizationId, asOfDate)
                        .or(() -> partnerRateCardRepository.findEffectiveDefault(asOfDate)))
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PARTNER_RATE_CARD_NOT_FOUND));
        checkCurrency(rateCard.getCurrencyCode(), currencyPolicy);
        RegionSurcharge surcharge = findRegionSurchargeOrThrow(region, asOfDate);
        checkCurrency(surcharge.getCurrencyCode(), currencyPolicy);

        BigDecimal baseFee = moneyCalculator.normalize(rateCard.getBaseFee(), currencyPolicy);
        BigDecimal tabletUnitRate = moneyCalculator.normalize(rateCard.getTabletUnitRate(), currencyPolicy);
        BigDecimal onsiteStaffDayRate = moneyCalculator.normalize(rateCard.getOnsiteStaffDayRate(), currencyPolicy);
        BigDecimal regionSurcharge = moneyCalculator.normalize(surcharge.getPartnerAmount(), currencyPolicy);
        BigDecimal adjustment = moneyCalculator.normalize(
                adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount, currencyPolicy
        );
        BigDecimal total = baseFee
                .add(tabletUnitRate.multiply(BigDecimal.valueOf(tabletCount)))
                .add(onsiteStaffDayRate.multiply(BigDecimal.valueOf(onsiteStaffPersonDays)))
                .add(regionSurcharge)
                .add(adjustment);
        if (total.signum() < 0) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        return new PartnerOrderAmount(
                baseFee, tabletCount, tabletUnitRate, onsiteStaffPersonDays, onsiteStaffDayRate,
                regionSurcharge, adjustment, moneyCalculator.normalize(total, currencyPolicy)
        );
    }

    /**
     * 견적 시점의 예상 발주 금액(기본 단가표, 조정 없음)을 계산하고 최소 마진을 검사한다 — 적자 견적을
     * 미리 막는다(6.3절). 실제 발주 금액은 발급할 때 파트너별 단가표로 다시 계산해 한 번 더 검사한다.
     */
    public BigDecimal estimatePartnerAmountWithinMargin(
            SalesQuoteCalculation calculation,
            ServiceRegion region,
            CurrencyPolicy currencyPolicy,
            LocalDate asOfDate
    ) {
        PartnerOrderAmount estimated = calculatePartnerOrderAmount(
                null, calculation.tabletCount(), calculation.onsiteStaffPersonDays(), region, BigDecimal.ZERO,
                currencyPolicy, asOfDate
        );
        platformOrderMarginPolicy.checkMargin(calculation.supplyAmount(), estimated.totalAmount());
        return estimated.totalAmount();
    }

    /** 파트너 전용 단가표가 기준일에 있는지(없으면 기본 단가표로 계산한다). */
    public boolean hasPartnerRateCard(Long organizationId, LocalDate asOfDate) {
        return partnerRateCardRepository.findEffectiveForPartner(organizationId, asOfDate).isPresent();
    }

    public BigDecimal minimumMarginRate() {
        return platformOrderMarginPolicy.minimumMarginRate();
    }

    public boolean isPartnerOrderMarginSufficient(BigDecimal customerSupplyAmount, BigDecimal partnerOrderAmount) {
        return platformOrderMarginPolicy.isMarginSufficient(customerSupplyAmount, partnerOrderAmount);
    }

    public void checkPartnerOrderMargin(BigDecimal customerSupplyAmount, BigDecimal partnerOrderAmount) {
        platformOrderMarginPolicy.checkMargin(customerSupplyAmount, partnerOrderAmount);
    }

    private SalesQuoteCalculation.Line buildLine(
            SalesOrderLineType lineType, UnitProduct unitProduct, String name, int quantity, BigDecimal unitPrice,
            String taxCode, CurrencyPolicy currencyPolicy, LocalDate asOfDate
    ) {
        BigDecimal normalizedUnitPrice = moneyCalculator.normalize(unitPrice, currencyPolicy);
        BigDecimal amount = moneyCalculator.normalize(normalizedUnitPrice.multiply(BigDecimal.valueOf(quantity)), currencyPolicy);
        TaxPolicy taxPolicy = taxPolicyResolver.resolve(TAX_COUNTRY_CODE, taxCode, asOfDate);
        BigDecimal taxAmount = moneyCalculator.calculateExclusiveTax(amount, taxPolicy.getRatePercent(), currencyPolicy);
        return new SalesQuoteCalculation.Line(
                lineType, unitProduct, name, quantity, normalizedUnitPrice, amount, taxCode, taxPolicy.getRatePercent(), taxAmount
        );
    }

    private RegionSurcharge findRegionSurchargeOrThrow(ServiceRegion region, LocalDate asOfDate) {
        return regionSurchargeRepository.findEffective(region, asOfDate)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.REGION_SURCHARGE_NOT_FOUND));
    }

    private static int sumIncluded(List<BillingPlanUnitProduct> planUnitProducts, UnitProductType type) {
        return planUnitProducts.stream()
                .filter(source -> source.getUnitProduct().getType() == type)
                .mapToInt(BillingPlanUnitProduct::getIncludedQuantity)
                .sum();
    }

    private static void checkCurrency(String priceCurrencyCode, CurrencyPolicy currencyPolicy) {
        if (!priceCurrencyCode.equals(currencyPolicy.currencyCode())) {
            throw new ApplicationException(OrderErrorCode.CURRENCY_MISMATCH);
        }
    }
}
