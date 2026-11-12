package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.billing.model.EffectiveDiscount;
import com.eformworks.signstage.backend.feature.billing.service.OrganizationDiscountService;
import com.eformworks.signstage.backend.feature.billing.service.OrganizationSubscriptionService;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 플랜 선택·해제·확정과 플랜 선택 이력(signstage-docs
 * business/ceremony-plan-confirmation-review.md). 플랜을 고르거나 확정할 때마다 그 시점의
 * 카탈로그 값을 {@link CeremonyPlanHistory} 스냅샷으로 남긴다. 2026-11-11
 * {@code CeremonyService}에서 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyPlanService {

    private final CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    private final CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final OrganizationSubscriptionService organizationSubscriptionService;
    private final OrganizationDiscountService organizationDiscountService;
    private final CeremonyAccessService ceremonyAccessService;
    private final CeremonyCatalogService ceremonyCatalogService;

    /**
     * DRAFT 상태에서만 플랜을 바꿀 수 있다 — 확정 후(IN_PROGRESS/COMPLETED) 시도하면 거부한다.
     * 호출할 때마다 {@link CeremonyPlanHistory}에 이력을 한 행 남긴다(3.2/3.4절).
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary updatePlan(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.ChangePlan request
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyAccessService.checkPartnerDirectCeremony(ceremony);
        checkCeremonyPlanChangeable(ceremony);

        BillingPlan newPlan = billingCatalogQueryService.findBillingPlanOrThrow(request.getBillingPlanId(), SalesChannel.PARTNER_DIRECT);
        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));
        BillingPlanDiscountPeriod newPlanPeriod = ceremonyCatalogService.resolveSellablePlanPeriod(newPlan, asOfDate);
        ceremonyCatalogService.checkCurrencyMatches(ceremony.getCurrencyCode(), ceremonyCatalogService.resolvePlanCurrency(newPlan, asOfDate));

        ceremony.changePlan(newPlan);
        recordPlanHistory(ceremony, newPlan, newPlanPeriod, asOfDate);

        return CeremonyDto.Response.CeremonySummary.from(ceremony);
    }

    /**
     * 플랜 선택을 해제한다 — 확정 전(DRAFT)에만 가능하다. 사용자 요청(2026-09-11) — 행사 수정
     * 화면의 "플랫폼 이용료" 탭에서 플랜을 고른 뒤 확정 전에 되돌릴 방법이 없었다. 이력
     * ({@code CeremonyPlanHistory})은 남기지 않는다 — 스냅샷은 확정 이후를 위한 것이고, DRAFT는
     * {@link CeremonyCatalogService#findLatestPlanHistoryForSnapshot}이 어차피 무시하는 상태라 남길 실익이 없다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary clearPlan(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyAccessService.checkPartnerDirectCeremony(ceremony);
        checkCeremonyPlanChangeable(ceremony);
        if (ceremony.getBillingPlan() == null) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_NOT_SELECTED);
        }

        ceremony.changePlan(null);

        return CeremonyDto.Response.CeremonySummary.from(ceremony);
    }

    /**
     * "플랜 확정" — DRAFT → IN_PROGRESS로 단방향 전이한다. 이후 플랜은 고정되고, 서명자/문서/
     * 하위 행사 등록이 열린다(3.1절). 확정을 취소하는 API는 두지 않는다(4.4절). 플랜을 아직
     * 한 번도 선택하지 않았으면(2026-09-10, 생성 시 플랜 선택을 미룰 수 있게 되면서 가능해짐 —
     * signstage-docs business/ceremony-registration-flow-and-billing-tab-separation-review.md)
     * 확정할 대상 자체가 없으므로 거부한다.
     *
     * <p>확정 직전에 오늘 날짜로 스냅샷을 한 번 더 찍는다(2026-09-11 사용자 요청 —
     * signstage-docs business/ceremony-plan-price-snapshot-consistency-review.md 3.2절).
     * DRAFT 동안 "플랫폼 이용료"는 매번 라이브로 재계산되는데, 정작 실제로 고정되는 값은
     * 마지막 플랜 선택/변경 시점의 스냅샷이라 그 사이 카탈로그 가격이 바뀌면 확정 직전 화면
     * 숫자와 실제 청구 숫자가 달라질 수 있었다 — 확정할 때마다 재스냅샷해서 "확정 버튼을
     * 누르는 순간 보이던 값 = 실제로 고정되는 값"을 보장한다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary confirmPlan(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyAccessService.checkPartnerDirectCeremony(ceremony);
        checkCeremonyPlanChangeable(ceremony);
        if (ceremony.getBillingPlan() == null) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_NOT_SELECTED);
        }

        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));
        BillingPlanDiscountPeriod planPeriod = ceremonyCatalogService.resolveSellablePlanPeriod(ceremony.getBillingPlan(), asOfDate);
        ceremonyCatalogService.checkCurrencyMatches(ceremony.getCurrencyCode(), ceremonyCatalogService.resolvePlanCurrency(ceremony.getBillingPlan(), asOfDate));
        recordPlanHistory(ceremony, ceremony.getBillingPlan(), planPeriod, asOfDate);

        ceremony.confirmPlan();
        organizationSubscriptionService.consumeForCeremonyConfirmation(ceremony.getOrganization().getId(), ceremony.getBillingPlan())
                .ifPresent(ceremony::linkSubscription);

        return CeremonyDto.Response.CeremonySummary.from(ceremony);
    }

    /** 최신순 — 가장 앞이 확정(또는 가장 최근 변경) 시점의 스냅샷이다. */
    public List<CeremonyDto.Response.PlanHistorySummary> findPlanHistory(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyPlanHistoryRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(this::toPlanHistorySummary)
                .toList();
    }

    /**
     * 플랫폼 관리자용 플랜 선택 이력 — {@link #findPlanHistory}와 달리 조직 멤버십을 요구하지
     * 않는다(위 {@link CeremonyService#findCeremonyByPlatformAdmin}과 같은 이유). 관리자 화면에 파트너사의
     * 플랜 선택 이력·구매 이력을 함께 보여주는 신규 "행사 이력" 화면이 쓴다(signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 8.6절 결정, 2026-09-11).
     */
    public List<CeremonyDto.Response.PlanHistorySummary> findPlanHistoryByPlatformAdmin(Long organizationId, Long ceremonyId) {
        ceremonyAccessService.findCeremonyInOrganizationForPlatformAdminOrThrow(organizationId, ceremonyId);
        return ceremonyPlanHistoryRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(this::toPlanHistorySummary)
                .toList();
    }

    /**
     * 플랜 변경/확정은 DRAFT 상태에서만 가능하다 — signstage-docs
     * business/ceremony-plan-confirmation-review.md 3.1/3.2절.
     */
    private void checkCeremonyPlanChangeable(Ceremony ceremony) {
        if (ceremony.getStatus() != CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_ALREADY_CONFIRMED);
        }
    }

    /**
     * Ceremony 생성 시(최초 플랜 선택)·{@link #updatePlan}·{@link #confirmPlan}에서 매번
     * 호출한다(3.4절). 그 순간 플랜이 포함하던 단위 상품 구성(포함 수량 × 그 순간 단가)을
     * {@link CeremonyPlanHistoryUnitProduct}로 함께 스냅샷한다 — 카탈로그 관리자가 나중에
     * 플랜의 구성/가격을 바꿔도 이 Ceremony는 영향받지 않아야 한다(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 5장).
     *
     * <p>포함된 단위 상품 중 그 날짜에 유효한 가격 기간이 하나도 없으면(카탈로그 관리자가
     * 가격 기간 사이에 공백을 남긴 경우) 예외를 던져 플랜 선택/변경/확정 자체를 막는다
     * (2026-09-11 사용자 요청 — signstage-docs
     * business/ceremony-plan-price-snapshot-consistency-review.md 3.1절). 추가구매 경로
     * ({@link CeremonyCatalogService#resolveSellableUnitProductPeriod})와 같은 기준이다 — 예전엔 이 경로만 조용히
     * 0원으로 스냅샷해서, 관리자가 실수로 가격 공백을 남기면 그 날짜에 플랜을 선택한
     * 파트너가 에러 없이 무료로 한도를 받는 위험이 있었다.
     */
    void recordPlanHistory(Ceremony ceremony, BillingPlan plan, BillingPlanDiscountPeriod planPeriod, LocalDate asOfDate) {
        // 조직×플랜 할인 오버라이드가 있으면 카탈로그 값 대신 이 값을 스냅샷한다
        // (signstage-docs business/organization-event-discount-pricing-review.md 4.1절,
        // 2026-08-21 재검토) — 그 시점의 값을 CeremonyPlanHistory에 고정해 두므로, 오버라이드를
        // 나중에 바꿔도 이미 만들어진 이 Ceremony에는 영향을 주지 않는다.
        EffectiveDiscount discount =
                organizationDiscountService.resolveBillingPlanDiscount(
                        ceremony.getOrganization(), plan.getId(),
                        planPeriod.getDiscount().getDiscountType(), planPeriod.getDiscount().getDiscountValue(),
                        asOfDate
                );
        CeremonyPlanHistory history = ceremonyPlanHistoryRepository.save(
                CeremonyPlanHistory.builder()
                        .ceremony(ceremony)
                        .billingPlan(plan)
                        .catalogDiscountType(planPeriod.getDiscount().getDiscountType())
                        .catalogDiscountValue(planPeriod.getDiscount().getDiscountValue())
                        .discountType(discount.type())
                        .discountValue(discount.value())
                        .build()
        );
        billingCatalogQueryService.findPlanUnitProducts(plan.getId()).forEach(source -> {
            UnitProduct unitProduct = source.getUnitProduct();
            UnitProductPricePeriod effective = billingCatalogQueryService.findEffectiveUnitProductPricePeriod(unitProduct.getId(), asOfDate)
                    .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_INACTIVE));
            ceremonyPlanHistoryUnitProductRepository.save(
                    CeremonyPlanHistoryUnitProduct.builder()
                            .ceremonyPlanHistory(history)
                            .unitProduct(unitProduct)
                            .includedQuantity(source.getIncludedQuantity())
                            .currencyCode(effective.getPriceInfo().getCurrencyCode())
                            .snapshotSalePrice(effective.getPriceInfo().getSalePrice())
                            .snapshotTaxCode(effective.getPriceInfo().getTaxCode())
                            .build()
            );
        });
    }

    /**
     * 플랫폼 수주 행사의 플랜 스냅샷 — 플랫폼 수주 플랜·단위 상품에는 할인·판매 가격 기간이 없고 파트너는
     * 플랫폼 이용료를 내지 않으므로, 할인은 0원 정액, 포함 단위 상품은 0원·행사 통화·수주 패키지 줄의
     * 세금 코드로 남긴다(signstage-docs business/platform-order-unit-product-composition-review.md U05).
     */
    void recordPlatformOrderPlanHistory(Ceremony ceremony, BillingPlan plan, String taxCode) {
        CeremonyPlanHistory history = ceremonyPlanHistoryRepository.save(
                CeremonyPlanHistory.builder()
                        .ceremony(ceremony)
                        .billingPlan(plan)
                        .catalogDiscountType(DiscountType.FIXED_AMOUNT)
                        .catalogDiscountValue(BigDecimal.ZERO)
                        .discountType(DiscountType.FIXED_AMOUNT)
                        .discountValue(BigDecimal.ZERO)
                        .build()
        );
        billingCatalogQueryService.findPlanUnitProducts(plan.getId()).forEach(source ->
                ceremonyPlanHistoryUnitProductRepository.save(
                        CeremonyPlanHistoryUnitProduct.builder()
                                .ceremonyPlanHistory(history)
                                .unitProduct(source.getUnitProduct())
                                .includedQuantity(source.getIncludedQuantity())
                                .currencyCode(ceremony.getCurrencyCode())
                                .snapshotSalePrice(BigDecimal.ZERO)
                                .snapshotTaxCode(taxCode)
                                .build()
                )
        );
    }

    private CeremonyDto.Response.PlanHistorySummary toPlanHistorySummary(CeremonyPlanHistory history) {
        List<CeremonyDto.Response.PlanHistoryLineSummary> lines = ceremonyPlanHistoryUnitProductRepository
                .findAllByCeremonyPlanHistoryId(history.getId()).stream()
                .map(line -> new CeremonyDto.Response.PlanHistoryLineSummary(
                        line.getUnitProduct().getId(),
                        line.getUnitProduct().getType().name(),
                        line.getUnitProduct().getName(),
                        line.getIncludedQuantity(),
                        line.getCurrencyCode(),
                        line.getSnapshotSalePrice(),
                        line.getSnapshotTaxCode()
                ))
                .toList();
        return new CeremonyDto.Response.PlanHistorySummary(
                history.getId(),
                history.getBillingPlan().getId(),
                history.getPlanName(),
                history.getPlanDiscountType().name(),
                history.getPlanDiscountValue(),
                lines,
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }
}
