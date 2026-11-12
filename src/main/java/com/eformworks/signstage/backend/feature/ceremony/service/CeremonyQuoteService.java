package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.OrganizationSubscription;
import com.eformworks.signstage.backend.feature.billing.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.billing.service.TaxPolicyResolver;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.model.QuoteCalculation;
import com.eformworks.signstage.backend.feature.ceremony.model.QuoteLineDetail;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 예상 청구 금액(플랫폼 이용료) 계산 — signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 3.5절 계산식.
 * {@link CustomerQuoteService}의 고객 견적 원가 산정도 같은 계산({@link #buildQuoteCalculation})을
 * 쓴다. 2026-11-11 {@code CeremonyService}에서 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyQuoteService {

    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final MoneyCalculator moneyCalculator;
    private final TaxPolicyResolver taxPolicyResolver;
    private final CeremonyAccessService ceremonyAccessService;
    private final CeremonyCatalogService ceremonyCatalogService;

    /**
     * 이 Ceremony의 예상 청구 금액 — signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 3.5절 계산식.
     * {@code 플랜 소계 = Σ(포함 단위 상품 snapshot가격 × includedQuantity)} → 플랜 자체 할인을
     * 한 번 적용 → 추가구매(승인분만, 정가 그대로 — 할인 없음)를 더해 subtotal → 행사 건별
     * 재량 할인({@code ceremony.finalDiscount})을 다시 한 번 적용 → 세금은 라인별 비례 배분 +
     * 라인별 taxCode로 계산한다(플랜 소계 자체도 그 안의 단위 상품 줄 수만큼 라인이 늘어난다 —
     * 옛 "플랜 항목 1줄"에서 "플랜에 포함된 단위 상품 줄 수"로 세분화됐을 뿐 알고리즘은 같다).
     *
     * <p>플랜 항목은 라이브 {@code BillingPlanUnitProduct}가 아니라 {@link CeremonyPlanHistory}
     * 최신 스냅샷({@link CeremonyPlanHistoryUnitProduct})을 쓴다 — 이력이 없는 행사(이 기능
     * 배포 전 기존 행사)만 라이브로 폴백한다. 추가구매는 승인(APPROVED)된 건만 반영하고, 그
     * 구매 시점 스냅샷(가격)을 그대로 쓴다.
     */
    public CeremonyDto.Response.EstimatedTotal calculateEstimatedTotal(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        QuoteCalculation calculation = buildQuoteCalculation(ceremony);
        return new CeremonyDto.Response.EstimatedTotal(
                calculation.planAppliedPrice(),
                calculation.unitProductPurchasesTotal(),
                calculation.subtotal(),
                ceremony.getFinalDiscount().getDiscountType().name(),
                ceremony.getFinalDiscount().getDiscountValue(),
                ceremony.getCurrencyCode(),
                ceremony.getCurrencyFractionDigits(),
                calculation.netAmount(),
                calculation.taxAmount(),
                calculation.grossAmount(),
                calculation.grossAmount(),
                ceremony.getSubscription() != null
        );
    }

    /**
     * {@link #calculateEstimatedTotal}("플랫폼 이용료", 옛 "예상 청구 금액")과
     * {@link com.eformworks.signstage.backend.feature.ceremony.service.CustomerQuoteService
     * #generateCustomerQuote}(고객 견적의 시스템 사용료 원가 산정)이 공유하는 계산 본체다 —
     * 같은 계산이 두 곳에서 갈라지면 서로 다른 숫자를 보여주는 사고가 나므로 소스를 하나로
     * 둔다. 옛 "확정 견적"({@code BillingQuoteService})도 이 메서드를 썼지만, 자가-체크아웃
     * 도입으로 그 기능 자체가 완전히 제거됐다(signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 6장 결정, 2026-09-11). 계산
     * 순서는 원래 설계 그대로: 플랜 소계 → 플랜 할인(품목 할인) → 추가구매 합산 → subtotal →
     * 행사 건별 재량 할인(ceremony 할인) → 세금(라인별 비례 배분 + 라인별 taxCode로 계산).
     * 호출자가 이미 조직/행사 접근 권한을 검증했다고 전제한다(이 메서드 자체는 검증하지 않음).
     *
     * <p>구독으로 확정한 행사({@code ceremony.subscription})는 플랜 금액을 구독으로 이미 냈으므로 플랜 줄을
     * 넣지 않고 추가구매만 센다 — "구독 포함 기본료 재청구 제외"(signstage-docs
     * business/platform-order-partner-subcontract-review.md D21).
     */
    QuoteCalculation buildQuoteCalculation(Ceremony ceremony) {
        CurrencyPolicy currencyPolicy = ceremony.currencyPolicy();
        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));

        List<QuoteLineDraft> planLines = ceremony.getSubscription() == null
                ? buildPlanLines(ceremony, asOfDate, currencyPolicy)
                : List.of();
        BigDecimal planAppliedPrice = planLines.stream()
                .map(QuoteLineDraft::afterItemDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ---- 추가구매 줄(승인분, 정가 그대로 — 품목 할인 없음, 3.5절 결정) ----
        List<QuoteLineDraft> purchaseLines = ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(ceremony.getId()).stream()
                .filter(line -> line.getPurchase().getStatus() == PurchaseStatus.APPROVED)
                .map(line -> {
                    BigDecimal listAmount = line.getPurchasedSalePrice().multiply(BigDecimal.valueOf(line.getQuantity()));
                    return new QuoteLineDraft(
                            "UNIT_PRODUCT_PURCHASE", line.getUnitProduct().getId(), line.getPurchasedName(),
                            line.getUnitProduct().getCategory(), line.getUnitProduct().isPlatformUsageFee(),
                            line.getQuantity(), line.getPurchasedSalePrice(), listAmount, BigDecimal.ZERO,
                            line.getPurchasedTaxCode()
                    );
                })
                .toList();
        BigDecimal purchaseTotal = purchaseLines.stream().map(QuoteLineDraft::listAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<QuoteLineDraft> allLines = new ArrayList<>(planLines);
        allLines.addAll(purchaseLines);

        BigDecimal subtotal = planAppliedPrice.add(purchaseTotal);
        BigDecimal netAmount = moneyCalculator.applyDiscount(
                subtotal, ceremony.getFinalDiscount().getDiscountType(), ceremony.getFinalDiscount().getDiscountValue(), currencyPolicy
        );

        List<QuoteLineDetail> lineDetails = allocateCeremonyDiscountAndTax(allLines, subtotal, netAmount, currencyPolicy, asOfDate);
        BigDecimal taxAmount = lineDetails.stream().map(QuoteLineDetail::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossAmount = moneyCalculator.normalize(netAmount.add(taxAmount), currencyPolicy);

        return new QuoteCalculation(planAppliedPrice, purchaseTotal, subtotal, netAmount, taxAmount, grossAmount, lineDetails);
    }

    /**
     * 구독으로 확정한 행사의 고객 견적 시스템 사용료 원가 — 구독 1회분(구매 금액 ÷ 허용 횟수) 중 플랜에서
     * 플랫폼 이용료 단위 상품이 차지하는 비중(플랜 할인 후 금액 기준)만큼이다(D21, 2026-11-12 결정).
     * 구독 행사가 아니면 0이다. 행사 건별 재량 할인은 구독 금액에 적용하지 않는다.
     */
    BigDecimal calculateSubscriptionUsageCost(Ceremony ceremony) {
        OrganizationSubscription subscription = ceremony.getSubscription();
        if (subscription == null || subscription.getPurchaseAmountSnapshot() == null) {
            return BigDecimal.ZERO;
        }
        CurrencyPolicy currencyPolicy = ceremony.currencyPolicy();
        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));
        List<QuoteLineDraft> planLines = buildPlanLines(ceremony, asOfDate, currencyPolicy);
        BigDecimal planTotal = planLines.stream().map(QuoteLineDraft::afterItemDiscount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (planTotal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal usageFeeTotal = planLines.stream()
                .filter(QuoteLineDraft::platformUsageFee)
                .map(QuoteLineDraft::afterItemDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int allowedCount = subscription.getAllowedCountSnapshot() == null ? 1 : Math.max(1, subscription.getAllowedCountSnapshot());
        BigDecimal share = subscription.getPurchaseAmountSnapshot()
                .multiply(usageFeeTotal)
                .divide(planTotal.multiply(BigDecimal.valueOf(allowedCount)), MathContext.DECIMAL64);
        return moneyCalculator.normalize(share, currencyPolicy);
    }

    /** 플랜 줄(품목 식별자 보존)에 플랜 할인(품목 할인)을 비례 배분한 결과. 플랜이 없으면 빈 목록이다. */
    private List<QuoteLineDraft> buildPlanLines(Ceremony ceremony, LocalDate asOfDate, CurrencyPolicy currencyPolicy) {
        List<QuoteLineDraft> planLines = new ArrayList<>();
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan != null) {
            Optional<CeremonyPlanHistory> snapshot = ceremonyCatalogService.findLatestPlanHistoryForSnapshot(ceremony);
            if (snapshot.isPresent()) {
                for (CeremonyPlanHistoryUnitProduct line : ceremonyPlanHistoryUnitProductRepository
                        .findAllByCeremonyPlanHistoryId(snapshot.get().getId())) {
                    if (line.getIncludedQuantity() <= 0) {
                        continue;
                    }
                    BigDecimal listAmount = line.getSnapshotSalePrice().multiply(BigDecimal.valueOf(line.getIncludedQuantity()));
                    planLines.add(new QuoteLineDraft(
                            "PLAN_UNIT_PRODUCT", line.getUnitProduct().getId(), line.getUnitProduct().getName(),
                            line.getUnitProduct().getCategory(), line.getUnitProduct().isPlatformUsageFee(),
                            line.getIncludedQuantity(), line.getSnapshotSalePrice(), listAmount, BigDecimal.ZERO,
                            line.getSnapshotTaxCode()
                    ));
                }
            } else {
                // 이력이 없는 경우(플랜 확정 기능 배포 전 기존 행사)만 라이브 값으로 대체한다.
                for (BillingPlanUnitProduct source : billingCatalogQueryService.findPlanUnitProducts(plan.getId())) {
                    if (source.getIncludedQuantity() <= 0) {
                        continue;
                    }
                    billingCatalogQueryService.findEffectiveUnitProductPricePeriod(source.getUnitProduct().getId(), asOfDate)
                            .ifPresent(period -> {
                                BigDecimal listAmount = period.getPriceInfo().getSalePrice()
                                        .multiply(BigDecimal.valueOf(source.getIncludedQuantity()));
                                planLines.add(new QuoteLineDraft(
                                        "PLAN_UNIT_PRODUCT", source.getUnitProduct().getId(), source.getUnitProduct().getName(),
                                        source.getUnitProduct().getCategory(), source.getUnitProduct().isPlatformUsageFee(),
                                        source.getIncludedQuantity(), period.getPriceInfo().getSalePrice(), listAmount,
                                        BigDecimal.ZERO, period.getPriceInfo().getTaxCode()
                                ));
                            });
                }
            }

            DiscountType planDiscountType = snapshot.map(CeremonyPlanHistory::getPlanDiscountType)
                    .orElseGet(() -> billingCatalogQueryService.findEffectivePlanDiscountPeriod(plan.getId(), asOfDate)
                            .map(period -> period.getDiscount().getDiscountType()).orElse(DiscountType.PERCENT));
            BigDecimal planDiscountValue = snapshot.map(CeremonyPlanHistory::getPlanDiscountValue)
                    .orElseGet(() -> billingCatalogQueryService.findEffectivePlanDiscountPeriod(plan.getId(), asOfDate)
                            .map(period -> period.getDiscount().getDiscountValue()).orElse(BigDecimal.ZERO));

            BigDecimal planSubtotal = planLines.stream().map(QuoteLineDraft::listAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal planApplied = moneyCalculator.applyDiscount(planSubtotal, planDiscountType, planDiscountValue, currencyPolicy);
            List<QuoteLineDraft> allocatedPlanLines = allocateItemDiscount(planLines, planApplied, currencyPolicy);
            planLines.clear();
            planLines.addAll(allocatedPlanLines);
        }
        return planLines;
    }

    /**
     * {@code appliedTotal}(품목 할인 적용 후 값)을 {@code lines}의 정가(listAmount) 비중대로
     * 재배분해 각 줄의 {@code itemDiscountAmount}를 채운다 — 반올림 잔액은 마지막 줄이
     * 흡수한다(결정적 배분 — 실행마다 같은 결과).
     */
    private List<QuoteLineDraft> allocateItemDiscount(List<QuoteLineDraft> lines, BigDecimal appliedTotal, CurrencyPolicy currencyPolicy) {
        BigDecimal listSum = lines.stream().map(QuoteLineDraft::listAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (listSum.signum() == 0) {
            return lines;
        }
        List<QuoteLineDraft> allocated = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;
        for (int index = 0; index < lines.size(); index++) {
            QuoteLineDraft line = lines.get(index);
            BigDecimal share;
            if (index == lines.size() - 1) {
                share = appliedTotal.subtract(runningTotal);
            } else {
                share = moneyCalculator.normalize(
                        line.listAmount().multiply(appliedTotal).divide(listSum, 12, currencyPolicy.roundingMode()),
                        currencyPolicy
                );
                runningTotal = runningTotal.add(share);
            }
            allocated.add(new QuoteLineDraft(
                    line.lineType(), line.itemId(), line.itemName(), line.category(), line.platformUsageFee(),
                    line.quantity(), line.unitListAmount(), line.listAmount(), line.listAmount().subtract(share), line.taxCode()
            ));
        }
        return allocated;
    }

    /**
     * 행사 건별 최종 할인 후 금액({@code netAmount})을 품목 할인 적용 후 금액 비중대로 라인에
     * 배분해 {@code ceremonyDiscountAmount}/{@code netAmount}를 채우고, 라인별 유효 세금
     * 정책으로 세액·합계를 계산한다({@code EXCLUSIVE} 세율 기준).
     */
    private List<QuoteLineDetail> allocateCeremonyDiscountAndTax(
            List<QuoteLineDraft> lines,
            BigDecimal subtotal,
            BigDecimal netAmount,
            CurrencyPolicy currencyPolicy,
            LocalDate taxPointDate
    ) {
        List<QuoteLineDetail> result = new ArrayList<>();
        if (subtotal.signum() == 0) {
            return result;
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < lines.size(); index++) {
            QuoteLineDraft line = lines.get(index);
            BigDecimal afterItemDiscount = line.afterItemDiscount();
            BigDecimal lineNet;
            if (index == lines.size() - 1) {
                lineNet = netAmount.subtract(allocated);
            } else {
                lineNet = moneyCalculator.normalize(
                        afterItemDiscount.multiply(netAmount).divide(subtotal, 12, currencyPolicy.roundingMode()),
                        currencyPolicy
                );
                allocated = allocated.add(lineNet);
            }
            BigDecimal ceremonyDiscount = afterItemDiscount.subtract(lineNet);
            TaxPolicy taxPolicy = taxPolicyResolver.resolve("KR", line.taxCode(), taxPointDate);
            BigDecimal taxAmount = moneyCalculator.calculateExclusiveTax(lineNet, taxPolicy.getRatePercent(), currencyPolicy);
            BigDecimal grossAmount = moneyCalculator.normalize(lineNet.add(taxAmount), currencyPolicy);
            result.add(new QuoteLineDetail(
                    line.lineType(), line.itemId(), line.itemName(), line.category(), line.platformUsageFee(),
                    line.quantity(), line.unitListAmount(), line.listAmount(), line.itemDiscountAmount(), ceremonyDiscount, lineNet,
                    line.taxCode(), taxPolicy.getCategory().name(), taxPolicy.getRatePercent(), taxPolicy.getPriceInclusion(),
                    taxAmount, grossAmount
            ));
        }
        return result;
    }

    /**
     * 품목 할인 배분 전 단계의 줄 초안 — {@code itemDiscountAmount}는 {@link #allocateItemDiscount} 전엔 0.
     * {@code platformUsageFee}는 "플랫폼 이용료 대상인지"를 그 시점 {@code UnitProduct.isPlatformUsageFee()}
     * 값으로 스냅샷한다(2026-09-12, signstage-docs
     * business/onsite-support-negotiation-and-billing-classification-review.md 3.1절
     * 결정) — {@code category}에서 다시 계산하지 않는다. {@code category}는 여전히 갖고
     * 있지만 이제 순수 참고용이다(이 판정에는 안 쓴다).
     */
    private record QuoteLineDraft(
            String lineType,
            Long itemId,
            String itemName,
            UnitProductCategory category,
            boolean platformUsageFee,
            int quantity,
            BigDecimal unitListAmount,
            BigDecimal listAmount,
            BigDecimal itemDiscountAmount,
            String taxCode
    ) {
        BigDecimal afterItemDiscount() {
            return listAmount.subtract(itemDiscountAmount);
        }
    }
}
