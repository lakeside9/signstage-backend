package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanDiscountPeriodHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanType;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.SubscriptionType;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanDiscountPeriodHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationSubscriptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사(Ceremony) 과금 플랜 카탈로그 — 단위 상품 묶음 + 전체 할인(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10, 3.3절).
 * 등록은 플랫폼 관리자 전용, 조회는 인증된 사용자 누구나 가능하다.
 *
 * <p>플랜은 더 이상 자기 가격을 갖지 않는다 — "오늘 가격"은
 * {@code Σ(unitProduct.effectivePrice(오늘) × includedQuantity)}로 조회 시점에 계산되고,
 * 그 합계에 적용할 할인만 {@link BillingPlanDiscountPeriod} 기간 단위 CRUD로 관리한다(다중버전
 * 채택, 옛 {@code BillingPlanPricePeriod}와 같은 패턴). 플랜이 포함하는 단위 상품 구성
 * ({@code unitProducts})은 {@link BillingPlanUnitProduct} 조인으로 통째로 교체하는 방식으로
 * 관리한다 — 옛 {@code capacities}/{@code optionalFeatureIds}/{@code capacityAddOnIds} 3개의
 * "통째로 교체" 패턴을 하나로 합쳤다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingPlanService {

    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    private final BillingPlanHistoryRepository billingPlanHistoryRepository;
    private final BillingPlanHistoryUnitProductRepository billingPlanHistoryUnitProductRepository;
    private final BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;
    private final BillingPlanDiscountPeriodHistoryRepository billingPlanDiscountPeriodHistoryRepository;
    private final UnitProductRepository unitProductRepository;
    private final UnitProductPricePeriodRepository unitProductPricePeriodRepository;
    private final CeremonyRepository ceremonyRepository;
    private final CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    private final OrganizationSubscriptionRepository organizationSubscriptionRepository;
    private final OrganizationBillingPlanDiscountRepository organizationBillingPlanDiscountRepository;
    private final OrganizationBillingPlanDiscountHistoryRepository organizationBillingPlanDiscountHistoryRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    @Transactional
    public BillingPlanDto.Response.BillingPlanSummary createPlan(
            String actingPlatformRole,
            Long adminUserId,
            BillingPlanDto.Request.CreatePlan request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        List<BillingPlanDto.Request.PlanUnitProductLine> lines = request.getUnitProducts() == null
                ? List.of() : request.getUnitProducts();
        Map<Long, UnitProduct> unitProducts = resolveUnitProducts(lines);

        LocalDate effectiveFrom = resolveEffectiveFrom(request.getEffectiveFrom());
        checkPeriodValid(effectiveFrom, request.getEffectiveTo());
        DiscountType discountType = parseDiscountType(request.getDiscountType());
        BillingPlanType planType = parseSubscriptionFields(request);

        BillingPlan plan = BillingPlan.builder()
                .name(request.getName())
                .planType(planType)
                .subscriptionType(planType == BillingPlanType.SUBSCRIPTION
                        ? SubscriptionType.valueOf(request.getSubscriptionType()) : null)
                .subscriptionPeriodMonths(planType == BillingPlanType.SUBSCRIPTION
                        ? request.getSubscriptionPeriodMonths() : null)
                .subscriptionAllowedCount(planType == BillingPlanType.SUBSCRIPTION
                        ? request.getSubscriptionAllowedCount() : null)
                .build();
        billingPlanRepository.save(plan);
        saveUnitProducts(plan, lines, unitProducts);
        recordPlanHistory(plan);

        BillingPlanDiscountPeriod period = BillingPlanDiscountPeriod.builder()
                .billingPlan(plan)
                .discountType(discountType)
                .discountValue(request.getDiscountValue())
                .active(request.getActive())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(request.getEffectiveTo())
                .build();
        billingPlanDiscountPeriodRepository.save(period);
        recordPeriodHistory(plan, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.CREATE_BILLING_PLAN,
                null,
                null,
                "planId=" + plan.getId() + ", name=" + plan.getName()
        );

        return toSummary(plan);
    }

    @Transactional
    public BillingPlanDto.Response.BillingPlanSummary updatePlan(
            Long planId,
            String actingPlatformRole,
            Long adminUserId,
            BillingPlanDto.Request.UpdatePlan request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        BillingPlan plan = billingPlanRepository.findById(planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));

        List<BillingPlanDto.Request.PlanUnitProductLine> lines = request.getUnitProducts() == null
                ? List.of() : request.getUnitProducts();
        Map<Long, UnitProduct> unitProducts = resolveUnitProducts(lines);

        String detail = "planId=" + planId + ", name: " + plan.getName() + " -> " + request.getName();

        plan.updateInfo(request.getName());

        // 단위 상품 구성 통째로 교체 — 이미 확정/진행 중인 Ceremony는 CeremonyPlanHistoryUnitProduct
        // 스냅샷으로 보호되어 이 변경에 영향받지 않는다.
        //
        // flush()가 반드시 필요하다: deleteAllByBillingPlanId는 파생 delete 쿼리라 대상을 조회해
        // EntityManager.remove()만 등록할 뿐 DELETE SQL을 즉시 내보내지 않는다(플러시 시점까지
        // 지연). 그런데 BillingPlanUnitProduct는 IDENTITY 채번이라 바로 다음 saveUnitProducts의
        // save()가 생성 키를 받으려고 INSERT를 즉시 실행한다 — 그 사이 flush가 없으면 이번
        // 수정에서도 그대로 남는 단위 상품(같은 billing_plan_id+unit_product_id 조합, 예를 들어
        // "수정하되 일부 상품은 그대로 유지")의 INSERT가 아직 DB에 남아있는 옛 행과 충돌해
        // `uq_bpup_plan_product` 유니크 제약 위반(Duplicate entry '{planId}-{unitProductId}')으로
        // 실패한다(2026-09-10, 실제 발생 사례로 발견 — 수정 시 가장 흔한 경로라 재현이 쉬웠다).
        billingPlanUnitProductRepository.deleteAllByBillingPlanId(planId);
        billingPlanUnitProductRepository.flush();
        saveUnitProducts(plan, lines, unitProducts);
        recordPlanHistory(plan);

        platformAdminAuditLogRecorder.record(adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null, detail);

        return toSummary(plan);
    }

    /**
     * 사용한 적이 없는 플랜만 삭제한다(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 11장, 2026-09-10 —
     * 단위 상품 삭제와 같은 조건). 행사(현재/이력)·조직 구독·조직×플랜 할인 오버라이드(현재/이력)
     * 어디에도 없으면 이 플랜 자신의 구성·할인 기간·편집 이력까지 함께 지운다 — DB에
     * {@code ON DELETE CASCADE}가 없어 자식부터 순서대로 지운다({@link UnitProductService#deleteUnitProduct}와
     * 같은 패턴).
     */
    @Transactional
    public void deletePlan(Long planId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        BillingPlan plan = billingPlanRepository.findById(planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        checkNeverUsed(planId);

        billingPlanHistoryUnitProductRepository.deleteAllByBillingPlanHistory_BillingPlanId(planId);
        billingPlanHistoryRepository.deleteAllByBillingPlanId(planId);
        billingPlanDiscountPeriodHistoryRepository.deleteAllByBillingPlanId(planId);
        billingPlanDiscountPeriodRepository.deleteAllByBillingPlanId(planId);
        billingPlanUnitProductRepository.deleteAllByBillingPlanId(planId);
        billingPlanRepository.delete(plan);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.DELETE_BILLING_PLAN, null, null,
                "planId=" + planId + ", name=" + plan.getName()
        );
    }

    private void checkNeverUsed(Long planId) {
        if (hasAnyUsage(planId)) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_IN_USE);
        }
    }

    /**
     * 이 플랜을 "다른 누군가"(행사·조직)가 참조한 적이 있는지 — 플랜 자신의 구성/할인 기간/편집
     * 이력({@code BillingPlanUnitProduct}/{@code BillingPlanHistory*}/{@code BillingPlanDiscountPeriod*})은
     * 여기 포함하지 않는다(삭제 시 함께 지워질 뿐인 플랜 자신의 데이터라 "사용"이 아니다).
     * {@code OrganizationSubscription}은 하드 삭제되지 않고 상태만 바뀌므로 그 존재 자체로
     * 과거 구독까지 커버되고, {@code OrganizationBillingPlanDiscount}는 하드 삭제될 수 있어
     * 살아있는 오버라이드뿐 아니라 그 이력까지 함께 봐야 한다.
     */
    private boolean hasAnyUsage(Long planId) {
        return ceremonyRepository.existsByBillingPlanId(planId)
                || ceremonyPlanHistoryRepository.existsByBillingPlanId(planId)
                || organizationSubscriptionRepository.existsByBillingPlanId(planId)
                || organizationBillingPlanDiscountRepository.existsByBillingPlanId(planId)
                || organizationBillingPlanDiscountHistoryRepository.existsByBillingPlanId(planId);
    }

    /** 새 할인 기간을 추가한다. */
    @Transactional
    public BillingPlanDto.Response.BillingPlanPeriodSummary createPeriod(
            Long planId,
            String actingPlatformRole,
            Long adminUserId,
            BillingPlanDto.Request.CreatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        BillingPlan plan = billingPlanRepository.findById(planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        LocalDate effectiveFrom = resolveEffectiveFrom(request.getEffectiveFrom());
        checkPeriodValid(effectiveFrom, request.getEffectiveTo());
        checkNoOverlap(planId, null, effectiveFrom, request.getEffectiveTo());

        BillingPlanDiscountPeriod period = BillingPlanDiscountPeriod.builder()
                .billingPlan(plan)
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .active(request.getActive())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(request.getEffectiveTo())
                .build();
        billingPlanDiscountPeriodRepository.save(period);
        recordPeriodHistory(plan, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null,
                "planId=" + planId + ", 할인 기간 생성: " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 이미 있는 할인 기간 하나를 고친다. */
    @Transactional
    public BillingPlanDto.Response.BillingPlanPeriodSummary updatePeriod(
            Long planId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            BillingPlanDto.Request.UpdatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        billingPlanRepository.findById(planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        BillingPlanDiscountPeriod period = billingPlanDiscountPeriodRepository.findByIdAndBillingPlanId(periodId, planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(planId, periodId, request.getEffectiveFrom(), request.getEffectiveTo());

        String previous = describe(period);
        period.update(
                parseDiscountType(request.getDiscountType()), request.getDiscountValue(),
                request.getActive(), request.getEffectiveFrom(), request.getEffectiveTo()
        );
        recordPeriodHistory(period.getBillingPlan(), period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null,
                "planId=" + planId + ", periodId=" + periodId + ", 할인 기간: " + previous + " -> " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 할인 기간을 제거한다 — 마지막 남은 기간 하나는 지울 수 없다(플랜은 항상 최소 1개 기간이 있어야 한다). */
    @Transactional
    public void removePeriod(Long planId, Long periodId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        billingPlanRepository.findById(planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        BillingPlanDiscountPeriod period = billingPlanDiscountPeriodRepository.findByIdAndBillingPlanId(periodId, planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        if (billingPlanDiscountPeriodRepository.countByBillingPlanId(planId) <= 1) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_REQUIRED);
        }

        recordPeriodHistory(period.getBillingPlan(), period, true);
        billingPlanDiscountPeriodRepository.delete(period);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null,
                "planId=" + planId + ", periodId=" + periodId + ", 할인 기간 제거: " + describe(period)
        );
    }

    /** 이 플랜의 할인 기간 전체(과거/현재/예정) — 오래된 순. */
    public List<BillingPlanDto.Response.BillingPlanPeriodSummary> findPlanPeriods(Long planId) {
        if (!billingPlanRepository.existsById(planId)) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND);
        }
        return billingPlanDiscountPeriodRepository.findAllByBillingPlanIdOrderByEffectiveFromAsc(planId).stream()
                .map(this::toPeriodSummary)
                .toList();
    }

    /** 할인 기간의 생성/수정/삭제 이력 — 최신순. */
    public List<BillingPlanDto.Response.BillingPlanPeriodHistorySummary> findPlanPeriodHistory(Long planId) {
        if (!billingPlanRepository.existsById(planId)) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND);
        }
        return billingPlanDiscountPeriodHistoryRepository.findAllByBillingPlanIdOrderByCreatedAtDesc(planId).stream()
                .map(h -> new BillingPlanDto.Response.BillingPlanPeriodHistorySummary(
                        h.getId(),
                        h.getDiscount().getDiscountType().name(),
                        h.getDiscount().getDiscountValue(),
                        h.isActive(),
                        h.getEffectiveFrom(),
                        h.getEffectiveTo(),
                        h.isRemoved(),
                        h.getCreatedBy(),
                        h.getCreatedAt()
                ))
                .toList();
    }

    public List<BillingPlanDto.Response.BillingPlanSummary> findPlans() {
        return billingPlanRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(이름 또는 단위 상품 구성이 바뀔 때). */
    public List<BillingPlanDto.Response.BillingPlanHistorySummary> findPlanHistory(Long planId) {
        if (!billingPlanRepository.existsById(planId)) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND);
        }
        return billingPlanHistoryRepository.findAllByBillingPlanIdOrderByCreatedAtDesc(planId).stream()
                .map(this::toHistorySummary)
                .toList();
    }

    /** 생성 시(최초 상태)와 {@link #updatePlan}에서 매 변경마다 호출한다. */
    private void recordPlanHistory(BillingPlan plan) {
        BillingPlanHistory history = billingPlanHistoryRepository.save(BillingPlanHistory.builder().billingPlan(plan).build());
        billingPlanUnitProductRepository.findAllByBillingPlanId(plan.getId()).forEach(source ->
                billingPlanHistoryUnitProductRepository.save(
                        BillingPlanHistoryUnitProduct.builder().billingPlanHistory(history).source(source).build()
                )
        );
    }

    /** 할인 기간 설정(생성/수정) 시점마다, 그리고 {@link #removePeriod}에서 제거 시점마다 호출한다. */
    private void recordPeriodHistory(BillingPlan plan, BillingPlanDiscountPeriod period, boolean removed) {
        billingPlanDiscountPeriodHistoryRepository.save(
                BillingPlanDiscountPeriodHistory.builder().billingPlan(plan).period(period).removed(removed).build()
        );
    }

    private void saveUnitProducts(
            BillingPlan plan,
            List<BillingPlanDto.Request.PlanUnitProductLine> lines,
            Map<Long, UnitProduct> unitProducts
    ) {
        for (BillingPlanDto.Request.PlanUnitProductLine line : lines) {
            billingPlanUnitProductRepository.save(
                    BillingPlanUnitProduct.builder()
                            .billingPlan(plan)
                            .unitProduct(unitProducts.get(line.getUnitProductId()))
                            .includedQuantity(line.getIncludedQuantity())
                            .build()
            );
        }
    }

    /**
     * 요청 줄들을 검증하고(id 존재, 수량 0 이상, 토글형 상한, 중복 없음) {@code UnitProduct} 맵으로
     * 정규화한다.
     */
    private Map<Long, UnitProduct> resolveUnitProducts(List<BillingPlanDto.Request.PlanUnitProductLine> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = lines.stream().map(BillingPlanDto.Request.PlanUnitProductLine::getUnitProductId).toList();
        if (ids.size() != ids.stream().distinct().count()) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        for (BillingPlanDto.Request.PlanUnitProductLine line : lines) {
            if (line.getIncludedQuantity() == null || line.getIncludedQuantity() < 0) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
        }
        List<UnitProduct> found = unitProductRepository.findAllByIdIn(ids);
        if (found.size() != ids.size()) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
        }
        Map<Long, UnitProduct> byId = new HashMap<>();
        found.forEach(unitProduct -> byId.put(unitProduct.getId(), unitProduct));

        // 토글형(EVENT_EFFECT_BUNDLE) 수량은 0 또는 1만 허용한다 — 행사 추가구매 쪽
        // (CeremonyService#purchaseUnitProducts)이 이미 같은 규칙을 강제하는 것과 동일하게,
        // 플랜의 기본 포함 수량에도 적용한다(UnitProductType#isToggle 참고).
        for (BillingPlanDto.Request.PlanUnitProductLine line : lines) {
            if (byId.get(line.getUnitProductId()).getType().isToggle() && line.getIncludedQuantity() > 1) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
        }
        return byId;
    }

    private DiscountType parseDiscountType(String discountType) {
        try {
            return DiscountType.valueOf(discountType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 구독형 플랜 조건 검증(signstage-docs
     * business/organization-event-discount-pricing-review.md 8.7절 결정, 2026-09-10).
     * planType 생략은 STANDARD(4개 필드 전부 null)로 취급한다. SUBSCRIPTION이면
     * subscriptionType·subscriptionAllowedCount가 필수이고, PERIOD_AND_COUNT는
     * subscriptionPeriodMonths가 6 또는 12여야 하며, COUNT_ONLY는 그 값을 가질 수 없다.
     */
    private BillingPlanType parseSubscriptionFields(BillingPlanDto.Request.CreatePlan request) {
        if (request.getPlanType() == null || request.getPlanType().isBlank()) {
            return BillingPlanType.STANDARD;
        }
        BillingPlanType planType;
        try {
            planType = BillingPlanType.valueOf(request.getPlanType());
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        if (planType != BillingPlanType.SUBSCRIPTION) {
            return planType;
        }

        SubscriptionType subscriptionType;
        try {
            subscriptionType = SubscriptionType.valueOf(request.getSubscriptionType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_PLAN_FIELDS_INVALID);
        }
        if (request.getSubscriptionAllowedCount() == null || request.getSubscriptionAllowedCount() <= 0) {
            throw new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_PLAN_FIELDS_INVALID);
        }
        boolean periodValid = subscriptionType == SubscriptionType.PERIOD_AND_COUNT
                ? (request.getSubscriptionPeriodMonths() != null
                        && (request.getSubscriptionPeriodMonths() == 6 || request.getSubscriptionPeriodMonths() == 12))
                : request.getSubscriptionPeriodMonths() == null;
        if (!periodValid) {
            throw new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_PLAN_FIELDS_INVALID);
        }
        return planType;
    }

    /**
     * effectiveFrom 생략(null) 시 "오늘"로 채운다 — signstage-docs
     * business/organization-discount-override-security-and-validity-period-review.md 결정
     * #5(2026-09-10) — 플랜 카탈로그는 조직/행사 스코프가 없어 플랫폼 기본 타임존(Asia/Seoul)을
     * 쓴다. 이미 있는 기간을 고치는 {@code UpdatePeriod}는 이 헬퍼를 쓰지 않는다 — 편집 중인
     * 기간의 시작일을 묵시적으로 오늘로 되돌리면 안 되므로 여전히 필수 입력이다.
     */
    private LocalDate resolveEffectiveFrom(LocalDate requested) {
        return requested != null ? requested : InternationalizationDefaults.today();
    }

    private void checkPeriodValid(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_PERIOD_INVALID);
        }
    }

    private void checkNoOverlap(Long planId, Long excludePeriodId, LocalDate newFrom, LocalDate newTo) {
        boolean overlaps = billingPlanDiscountPeriodRepository.findAllByBillingPlanIdOrderByEffectiveFromAsc(planId).stream()
                .filter(p -> excludePeriodId == null || !p.getId().equals(excludePeriodId))
                .anyMatch(p -> rangesOverlap(newFrom, newTo, p.getEffectiveFrom(), p.getEffectiveTo()));
        if (overlaps) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_OVERLAPPING);
        }
    }

    /** null인 종료일은 무한대로 취급한다. */
    private boolean rangesOverlap(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        boolean aStartsBeforeBEnds = bTo == null || !aFrom.isAfter(bTo);
        boolean bStartsBeforeAEnds = aTo == null || !bFrom.isAfter(aTo);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    /**
     * PENDING(아직 effectiveFrom 전)/ON_SALE(오늘이 기간 안이고 active)/EXPIRED(effectiveTo가
     * 지남)/INACTIVE(기간 안이지만 active=false) — 관리 화면 배지용.
     */
    private String computeStatus(boolean active, LocalDate effectiveFrom, LocalDate effectiveTo) {
        LocalDate today = InternationalizationDefaults.today();
        if (today.isBefore(effectiveFrom)) {
            return "PENDING";
        }
        if (effectiveTo != null && today.isAfter(effectiveTo)) {
            return "EXPIRED";
        }
        return active ? "ON_SALE" : "INACTIVE";
    }

    private String describe(BillingPlanDiscountPeriod period) {
        return period.getDiscount().getDiscountType() + " " + period.getDiscount().getDiscountValue()
                + " (" + period.getEffectiveFrom() + " ~ " + (period.getEffectiveTo() == null ? "무기한" : period.getEffectiveTo()) + ")";
    }

    private BillingPlanDto.Response.BillingPlanSummary toSummary(BillingPlan plan) {
        Optional<BillingPlanDiscountPeriod> effective =
                billingPlanDiscountPeriodRepository.findEffective(plan.getId(), InternationalizationDefaults.today());
        List<BillingPlanDto.Response.PlanUnitProductLineSummary> lines =
                billingPlanUnitProductRepository.findAllByBillingPlanId(plan.getId()).stream()
                        .map(this::toLineSummary)
                        .toList();
        return new BillingPlanDto.Response.BillingPlanSummary(
                plan.getId(),
                plan.getName(),
                lines,
                ceremonyRepository.countByBillingPlanId(plan.getId()),
                plan.getCreatedAt(),
                plan.getPlanType().name(),
                plan.isSubscription(),
                plan.getSubscriptionType() != null ? plan.getSubscriptionType().name() : null,
                plan.getSubscriptionPeriodMonths(),
                plan.getSubscriptionAllowedCount(),
                effective.map(p -> p.getDiscount().getDiscountType().name()).orElse(null),
                effective.map(p -> p.getDiscount().getDiscountValue()).orElse(null),
                effective.map(BillingPlanDiscountPeriod::isActive).orElse(null),
                effective.map(BillingPlanDiscountPeriod::getEffectiveFrom).orElse(null),
                effective.map(BillingPlanDiscountPeriod::getEffectiveTo).orElse(null),
                effective.map(p -> computeStatus(p.isActive(), p.getEffectiveFrom(), p.getEffectiveTo())).orElse("NO_ACTIVE_PERIOD"),
                !hasAnyUsage(plan.getId())
        );
    }

    private BillingPlanDto.Response.PlanUnitProductLineSummary toLineSummary(BillingPlanUnitProduct source) {
        UnitProduct unitProduct = source.getUnitProduct();
        Optional<UnitProductPricePeriod> effective =
                unitProductPricePeriodRepository.findEffective(unitProduct.getId(), InternationalizationDefaults.today());
        return new BillingPlanDto.Response.PlanUnitProductLineSummary(
                unitProduct.getId(),
                unitProduct.getType().name(),
                unitProduct.getName(),
                unitProduct.getCategory().name(),
                source.getIncludedQuantity(),
                effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null)
        );
    }

    private BillingPlanDto.Response.BillingPlanHistorySummary toHistorySummary(BillingPlanHistory history) {
        List<BillingPlanDto.Response.PlanUnitProductLineSummary> lines =
                billingPlanHistoryUnitProductRepository.findAllByBillingPlanHistoryId(history.getId()).stream()
                        .map(snapshot -> {
                            UnitProduct unitProduct = snapshot.getUnitProduct();
                            return new BillingPlanDto.Response.PlanUnitProductLineSummary(
                                    unitProduct.getId(),
                                    unitProduct.getType().name(),
                                    unitProduct.getName(),
                                    unitProduct.getCategory().name(),
                                    snapshot.getIncludedQuantity(),
                                    null,
                                    null
                            );
                        })
                        .toList();
        return new BillingPlanDto.Response.BillingPlanHistorySummary(
                history.getId(),
                history.getName(),
                lines,
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private BillingPlanDto.Response.BillingPlanPeriodSummary toPeriodSummary(BillingPlanDiscountPeriod period) {
        return new BillingPlanDto.Response.BillingPlanPeriodSummary(
                period.getId(),
                period.getDiscount().getDiscountType().name(),
                period.getDiscount().getDiscountValue(),
                period.isActive(),
                period.getEffectiveFrom(),
                period.getEffectiveTo(),
                computeStatus(period.isActive(), period.getEffectiveFrom(), period.getEffectiveTo()),
                period.getCreatedAt()
        );
    }

    /** signstage-docs business/menu-and-action-permission-management-review.md 10장 참고. */
    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
