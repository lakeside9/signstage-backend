package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.error.BillingErrorCode;
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
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteAdjustment;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCustomItem;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteOption;
import com.eformworks.signstage.backend.feature.order.model.ScopeExtras;
import com.eformworks.signstage.backend.feature.order.repository.CustomerPriceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerRateCardRepository;
import com.eformworks.signstage.backend.feature.order.repository.RegionSurchargeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
 *   <li>고객 견적: 패키지(플랜) 가격 + 옵션(단위 상품) 가격 × 수량 + 지역 할증 − 영업 할인 − 공급가 단수 절사. 줄마다
 *       세금 별도 부가세를 계산한다({@code CeremonyQuoteService}와 같은 세율 조회).</li>
 *   <li>범위 수량: 태블릿·와이파이 장비·현장 인력은 플랜 포함 수량과 옵션 수량을 더한다. 현장 인력
 *       ({@code ONSITE_SUPPORT}) 수량은 인·일로 본다.</li>
 *   <li>발주 금액: 파트너 예외 단가표가 있으면 그것을, 없으면 기본 단가표를 쓴다(D05·D28 안 A).</li>
 *   <li>추가 범위({@link ScopeExtras}): 리허설 별도일 인력은 고객에게 현장 인력 인·일 가격으로, 숙박은 권역 1박
 *       금액으로 청구하고 발주에도 더한다. 예비 태블릿은 발주에만 더한다(D23·D24·D26).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderPricingService {

    private static final String TAX_COUNTRY_CODE = "KR";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final CustomerPriceRepository customerPriceRepository;
    private final RegionSurchargeRepository regionSurchargeRepository;
    private final PartnerRateCardRepository partnerRateCardRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final TaxPolicyResolver taxPolicyResolver;
    private final MoneyCalculator moneyCalculator;
    private final PlatformOrderMarginPolicy platformOrderMarginPolicy;

    /**
     * @param adjustment 영업 할인(정액/퍼센트)과 공급가 절사 단위. 할인 → 절사 → 부가세 순서로 적용한다.
     */
    public SalesQuoteCalculation calculateSalesQuote(
            BillingPlan plan,
            List<SalesQuoteOption> options,
            List<SalesQuoteCustomItem> customItems,
            ServiceRegion region,
            ScopeExtras extras,
            SalesQuoteAdjustment adjustment,
            CurrencyPolicy currencyPolicy,
            LocalDate asOfDate
    ) {
        checkExtras(extras);
        if (!plan.isPlatformOrder()) {
            throw new ApplicationException(BillingErrorCode.SALES_CHANNEL_MISMATCH);
        }
        List<SalesQuoteCalculation.Line> lines = new ArrayList<>();
        List<BillingPlanUnitProduct> planUnitProducts = billingCatalogQueryService.findPlanUnitProducts(plan.getId());
        int tabletCount = sumIncluded(planUnitProducts, UnitProductType.TABLETS);
        int wifiCount = sumIncluded(planUnitProducts, UnitProductType.WIFI_EQUIPMENT);
        int onsiteStaffPersonDays = sumIncluded(planUnitProducts, UnitProductType.ONSITE_SUPPORT);

        CustomerPrice planPrice = customerPriceRepository.findEffectiveForPlan(plan.getId(), asOfDate)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.CUSTOMER_PRICE_NOT_FOUND));
        checkCurrency(planPrice.getCurrencyCode(), currencyPolicy);
        lines.add(buildLine(SalesOrderLineType.BILLING_PLAN, null, plan.getName(), 1,
                planPrice.getUnitPrice(), planPrice.getTaxCode(), planPrice.getId(), currencyPolicy, asOfDate));

        Set<Long> seenUnitProductIds = new HashSet<>();
        for (SalesQuoteOption option : options == null ? List.<SalesQuoteOption>of() : options) {
            if (!seenUnitProductIds.add(option.unitProductId())) {
                throw new ApplicationException(OrderErrorCode.OPTION_DUPLICATED);
            }
            UnitProduct unitProduct = billingCatalogQueryService.findUnitProductOrThrow(option.unitProductId(), SalesChannel.PLATFORM_ORDER);
            int quantity = unitProduct.getType().isToggle() ? 1 : option.quantity();
            if (quantity < 1) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
            // 고객 가격은 개당 단가이고 수량은 개수다 — 묶음 판매 상품은 판매 단위의 배수로만 넣는다(직접 수주 추가구매와 같은 규칙).
            if (quantity % unitProduct.getSaleUnitQuantity() != 0) {
                throw new ApplicationException(OrderErrorCode.OPTION_SALE_UNIT_MISMATCH);
            }
            CustomerPrice optionPrice = customerPriceRepository.findEffectiveForUnitProduct(unitProduct.getId(), asOfDate)
                    .orElseThrow(() -> new ApplicationException(OrderErrorCode.CUSTOMER_PRICE_NOT_FOUND));
            checkCurrency(optionPrice.getCurrencyCode(), currencyPolicy);
            lines.add(buildLine(SalesOrderLineType.UNIT_PRODUCT, unitProduct, unitProduct.getName(), quantity,
                    optionPrice.getUnitPrice(), optionPrice.getTaxCode(), optionPrice.getId(), currencyPolicy, asOfDate));
            if (unitProduct.getType() == UnitProductType.TABLETS) {
                tabletCount += quantity;
            } else if (unitProduct.getType() == UnitProductType.WIFI_EQUIPMENT) {
                wifiCount += quantity;
            } else if (unitProduct.getType() == UnitProductType.ONSITE_SUPPORT) {
                onsiteStaffPersonDays += quantity;
            }
        }

        // 직접 입력 항목은 고객 가격표가 없으므로 패키지 세금 코드를 쓴다.
        BigDecimal customItemPartnerAmount = BigDecimal.ZERO;
        for (SalesQuoteCustomItem item : customItems == null ? List.<SalesQuoteCustomItem>of() : customItems) {
            BigDecimal partnerUnitPrice = item.partnerUnitPrice() == null ? BigDecimal.ZERO : item.partnerUnitPrice();
            if (item.name() == null || item.name().isBlank() || item.quantity() < 1
                    || item.unitPrice() == null || item.unitPrice().signum() < 0 || partnerUnitPrice.signum() < 0) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
            SalesQuoteCalculation.Line line = buildLine(SalesOrderLineType.CUSTOM_ITEM, null, item.name().trim(), item.quantity(),
                    item.unitPrice(), planPrice.getTaxCode(), null, currencyPolicy, asOfDate);
            BigDecimal normalizedPartnerUnitPrice = moneyCalculator.normalize(partnerUnitPrice, currencyPolicy);
            lines.add(new SalesQuoteCalculation.Line(
                    line.lineType(), null, line.name(), line.quantity(), line.unitPrice(), line.amount(), line.taxCode(),
                    line.taxRatePercent(), line.taxAmount(), null, normalizedPartnerUnitPrice
            ));
            customItemPartnerAmount = customItemPartnerAmount.add(
                    moneyCalculator.normalize(normalizedPartnerUnitPrice.multiply(BigDecimal.valueOf(item.quantity())), currencyPolicy)
            );
        }

        RegionSurcharge surcharge = findRegionSurchargeOrThrow(region, asOfDate);
        checkCurrency(surcharge.getCurrencyCode(), currencyPolicy);
        if (surcharge.getCustomerAmount().signum() > 0) {
            lines.add(buildLine(SalesOrderLineType.REGION_SURCHARGE, null, "지역 할증(" + region.label() + ")", 1,
                    surcharge.getCustomerAmount(), surcharge.getTaxCode(), null, currencyPolicy, asOfDate));
        }
        if (extras.rehearsalStaffPersonDays() > 0) {
            UnitProduct onsiteSupport = billingCatalogQueryService.findFirstUnitProductByType(UnitProductType.ONSITE_SUPPORT, SalesChannel.PLATFORM_ORDER)
                    .orElseThrow(() -> new ApplicationException(OrderErrorCode.CUSTOMER_PRICE_NOT_FOUND));
            CustomerPrice staffPrice = customerPriceRepository.findEffectiveForUnitProduct(onsiteSupport.getId(), asOfDate)
                    .orElseThrow(() -> new ApplicationException(OrderErrorCode.CUSTOMER_PRICE_NOT_FOUND));
            checkCurrency(staffPrice.getCurrencyCode(), currencyPolicy);
            lines.add(buildLine(SalesOrderLineType.REHEARSAL_STAFF, onsiteSupport, "리허설 현장 인력(별도일)",
                    extras.rehearsalStaffPersonDays(), staffPrice.getUnitPrice(), staffPrice.getTaxCode(), staffPrice.getId(),
                    currencyPolicy, asOfDate));
        }
        if (extras.lodgingPersonNights() > 0) {
            lines.add(buildLine(SalesOrderLineType.LODGING, null,
                    "숙박(" + extras.lodgingNights() + "박 × " + extras.lodgingPersons() + "명)", extras.lodgingPersonNights(),
                    surcharge.getLodgingCustomerAmount(), surcharge.getTaxCode(), null, currencyPolicy, asOfDate));
        }

        BigDecimal subtotal = lines.stream().map(SalesQuoteCalculation.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (adjustment == null) {
            adjustment = SalesQuoteAdjustment.NONE;
        }
        BigDecimal discountRatePercent = adjustment.discountRatePercent();
        BigDecimal discountAmount = adjustment.discountAmount();
        BigDecimal discount;
        String discountName = "영업 할인";
        if (discountRatePercent != null) {
            if (discountRatePercent.signum() < 0 || discountRatePercent.compareTo(ONE_HUNDRED) > 0) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
            discount = moneyCalculator.normalize(subtotal.multiply(discountRatePercent).divide(ONE_HUNDRED), currencyPolicy);
            discountName = "영업 할인(" + discountRatePercent.stripTrailingZeros().toPlainString() + "%)";
        } else {
            discount = discountAmount == null ? BigDecimal.ZERO : moneyCalculator.normalize(discountAmount, currencyPolicy);
        }
        if (discount.signum() < 0 || discount.compareTo(subtotal) > 0) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        if (discount.signum() > 0) {
            // 할인은 패키지 세율로 음수 줄을 둔다 — 줄별 세액 합계가 그대로 부가세가 되게 한다.
            SalesQuoteCalculation.Line discountLine = buildLine(SalesOrderLineType.DISCOUNT, null, discountName, 1,
                    discount, planPrice.getTaxCode(), null, currencyPolicy, asOfDate);
            lines.add(new SalesQuoteCalculation.Line(
                    SalesOrderLineType.DISCOUNT, null, discountLine.name(), 1,
                    discountLine.unitPrice().negate(), discountLine.amount().negate(),
                    discountLine.taxCode(), discountLine.taxRatePercent(), discountLine.taxAmount().negate(), null
            ));
        }

        if (adjustment.truncationUnit() != null) {
            addTruncationLine(lines, adjustment.truncationUnit(), planPrice.getTaxCode(), currencyPolicy, asOfDate);
        }

        BigDecimal supplyAmount = lines.stream().map(SalesQuoteCalculation.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vatAmount = lines.stream().map(SalesQuoteCalculation.Line::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalesQuoteCalculation(
                lines, supplyAmount, vatAmount, tabletCount, onsiteStaffPersonDays, extras, surcharge.getId(), wifiCount,
                customItemPartnerAmount
        );
    }

    /**
     * @param organizationId 발주 대상 파트너. null이면 기본 단가표로 계산한다(견적 시점 예상 발주 금액).
     */
    public PartnerOrderAmount calculatePartnerOrderAmount(
            Long organizationId,
            int tabletCount,
            int wifiCount,
            int onsiteStaffPersonDays,
            ScopeExtras extras,
            BigDecimal customItemAmount,
            ServiceRegion region,
            BigDecimal adjustmentAmount,
            CurrencyPolicy currencyPolicy,
            LocalDate asOfDate
    ) {
        checkExtras(extras);
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
        BigDecimal wifiUnitRate = moneyCalculator.normalize(rateCard.getWifiUnitRate(), currencyPolicy);
        BigDecimal onsiteStaffDayRate = moneyCalculator.normalize(rateCard.getOnsiteStaffDayRate(), currencyPolicy);
        BigDecimal regionSurcharge = moneyCalculator.normalize(surcharge.getPartnerAmount(), currencyPolicy);
        BigDecimal lodgingNightRate = moneyCalculator.normalize(surcharge.getLodgingPartnerAmount(), currencyPolicy);
        BigDecimal customItems = moneyCalculator.normalize(
                customItemAmount == null ? BigDecimal.ZERO : customItemAmount, currencyPolicy
        );
        BigDecimal adjustment = moneyCalculator.normalize(
                adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount, currencyPolicy
        );
        BigDecimal total = baseFee
                .add(tabletUnitRate.multiply(BigDecimal.valueOf(tabletCount + extras.spareTabletCount())))
                .add(wifiUnitRate.multiply(BigDecimal.valueOf(wifiCount)))
                .add(onsiteStaffDayRate.multiply(BigDecimal.valueOf(onsiteStaffPersonDays + extras.rehearsalStaffPersonDays())))
                .add(regionSurcharge)
                .add(lodgingNightRate.multiply(BigDecimal.valueOf(extras.lodgingPersonNights())))
                .add(customItems)
                .add(adjustment);
        if (total.signum() < 0) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        return new PartnerOrderAmount(
                baseFee, tabletCount, tabletUnitRate, onsiteStaffPersonDays, onsiteStaffDayRate,
                regionSurcharge, adjustment, moneyCalculator.normalize(total, currencyPolicy),
                extras.spareTabletCount(), extras.rehearsalStaffPersonDays(), extras.lodgingPersonNights(), lodgingNightRate,
                rateCard.getId(), surcharge.getId(), wifiCount, wifiUnitRate, customItems
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
                null, calculation.tabletCount(), calculation.wifiCount(), calculation.onsiteStaffPersonDays(), calculation.extras(),
                calculation.customItemPartnerAmount(), region, BigDecimal.ZERO,
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

    /**
     * 할인 뒤 공급가(부가세 별도) 합계를 절사 단위 미만에서 버리는 음수 줄을 더한다. 줄 세액은 버린 금액에 패키지 세율로
     * 계산해 빼므로 부가세는 절사한 공급가 기준이 된다.
     */
    private void addTruncationLine(
            List<SalesQuoteCalculation.Line> lines, int truncationUnit, String taxCode, CurrencyPolicy currencyPolicy, LocalDate asOfDate
    ) {
        if (!SalesQuoteAdjustment.TRUNCATION_UNITS.contains(truncationUnit)) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        BigDecimal supplyAmount = lines.stream().map(SalesQuoteCalculation.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = supplyAmount.remainder(BigDecimal.valueOf(truncationUnit));
        if (remainder.signum() <= 0) {
            return;
        }
        SalesQuoteCalculation.Line line = buildLine(SalesOrderLineType.TRUNCATION, null,
                "단수 절사(" + String.format("%,d", truncationUnit) + " 단위)", 1, remainder, taxCode, null, currencyPolicy, asOfDate);
        lines.add(new SalesQuoteCalculation.Line(
                line.lineType(), null, line.name(), 1, line.unitPrice().negate(), line.amount().negate(),
                line.taxCode(), line.taxRatePercent(), line.taxAmount().negate(), null
        ));
    }

    /**
     * 견적서 줄 아래 보이는 설명 — 견적을 저장할 때 줄에 복사한다. 패키지 줄은 플랜 구성에서 "견적서 표시"를 켠
     * 상품의 포함 수량(한 줄에 하나, "서명자 1,500명: 견적 설명"), 옵션 줄은 그 단위 상품의 견적 설명이다.
     * 나머지 줄은 설명이 없다.
     */
    public String describeLine(SalesQuoteCalculation.Line line, BillingPlan plan) {
        return switch (line.lineType()) {
            case BILLING_PLAN -> describePlanInclusions(plan);
            case UNIT_PRODUCT -> line.unitProduct() != null ? line.unitProduct().getQuoteDescription() : null;
            default -> null;
        };
    }

    private String describePlanInclusions(BillingPlan plan) {
        List<String> rows = billingCatalogQueryService.findPlanUnitProducts(plan.getId()).stream()
                .filter(source -> source.getIncludedQuantity() > 0)
                .filter(BillingPlanUnitProduct::isShowOnQuote)
                .sorted(Comparator.comparing((BillingPlanUnitProduct source) -> source.getUnitProduct().getDisplayOrder(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(source -> {
                    UnitProduct unitProduct = source.getUnitProduct();
                    String row = unitProduct.getName() + " " + String.format("%,d", source.totalQuantity())
                            + unitProduct.getType().quantityUnit();
                    return unitProduct.getQuoteDescription() == null ? row : row + ": " + unitProduct.getQuoteDescription();
                })
                .toList();
        return rows.isEmpty() ? null : String.join("\n", rows);
    }

    private SalesQuoteCalculation.Line buildLine(
            SalesOrderLineType lineType, UnitProduct unitProduct, String name, int quantity, BigDecimal unitPrice,
            String taxCode, Long customerPriceId, CurrencyPolicy currencyPolicy, LocalDate asOfDate
    ) {
        BigDecimal normalizedUnitPrice = moneyCalculator.normalize(unitPrice, currencyPolicy);
        BigDecimal amount = moneyCalculator.normalize(normalizedUnitPrice.multiply(BigDecimal.valueOf(quantity)), currencyPolicy);
        TaxPolicy taxPolicy = taxPolicyResolver.resolve(TAX_COUNTRY_CODE, taxCode, asOfDate);
        BigDecimal taxAmount = moneyCalculator.calculateExclusiveTax(amount, taxPolicy.getRatePercent(), currencyPolicy);
        return new SalesQuoteCalculation.Line(
                lineType, unitProduct, name, quantity, normalizedUnitPrice, amount, taxCode, taxPolicy.getRatePercent(), taxAmount,
                customerPriceId
        );
    }

    private RegionSurcharge findRegionSurchargeOrThrow(ServiceRegion region, LocalDate asOfDate) {
        return regionSurchargeRepository.findEffective(region, asOfDate)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.REGION_SURCHARGE_NOT_FOUND));
    }

    private static int sumIncluded(List<BillingPlanUnitProduct> planUnitProducts, UnitProductType type) {
        return planUnitProducts.stream()
                .filter(source -> source.getUnitProduct().getType() == type)
                .mapToInt(BillingPlanUnitProduct::totalQuantity)
                .sum();
    }

    private static void checkExtras(ScopeExtras extras) {
        if (extras == null || !extras.isValid()) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private static void checkCurrency(String priceCurrencyCode, CurrencyPolicy currencyPolicy) {
        if (!priceCurrencyCode.equals(currencyPolicy.currencyCode())) {
            throw new ApplicationException(OrderErrorCode.CURRENCY_MISMATCH);
        }
    }
}
