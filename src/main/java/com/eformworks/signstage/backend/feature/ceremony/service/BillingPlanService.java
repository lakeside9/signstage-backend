package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanCapacity;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanCapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistoryCapacity;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanPricePeriodHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanCapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanCapacityRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryCapacityRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanPricePeriodHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 행사(Ceremony) 과금 플랜 카탈로그. signstage-docs business/ceremony-billing-options-review.md
 * 4.2/4.9절 참고 — 필수옵션(서명자/템플릿/테스트·본행사 수 한도)은 모든 플랜이 항상 값을 가지며,
 * 그 값에 별도 시스템 상한을 코드로 두지 않는다(운영자가 카탈로그를 만들 때 정하는 값 그대로 쓴다).
 * 등록은 플랫폼 관리자 전용, 조회는 인증된 사용자 누구나 가능하다.
 *
 * <p>한도 구성({@code capacities})은 예전엔 {@code BillingPlan}의 고정 컬럼 5개였는데,
 * {@link BillingPlanCapacity} 조인 테이블로 일반화됐다(signstage-docs
 * business/billing-catalog-zero-base-schema-redesign-review.md 결정, 2026-09-08, 항목 B) —
 * {@code optionalFeatureIds}/{@code capacityAddOnIds}와 같은 "통째로 교체" 패턴으로 관리한다.
 *
 * <p>가격정보/사용여부는 이 서비스가 {@link BillingPlanPricePeriod} 기간 단위 CRUD로 관리한다
 * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정, 2026-09-09,
 * 다중버전 채택) — {@code OrganizationDiscountService}의 기간 CRUD와 같은 패턴(겹침 검증은
 * 서비스 레이어, "오늘" 유효한 기간은 {@code findEffective} 지연 조회).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingPlanService {

    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanCapacityRepository billingPlanCapacityRepository;
    private final BillingPlanHistoryCapacityRepository billingPlanHistoryCapacityRepository;
    private final OptionalFeatureRepository optionalFeatureRepository;
    private final BillingPlanOptionalFeatureRepository billingPlanOptionalFeatureRepository;
    private final CapacityAddOnRepository capacityAddOnRepository;
    private final BillingPlanCapacityAddOnRepository billingPlanCapacityAddOnRepository;
    private final BillingPlanHistoryRepository billingPlanHistoryRepository;
    private final BillingPlanPricePeriodRepository billingPlanPricePeriodRepository;
    private final BillingPlanPricePeriodHistoryRepository billingPlanPricePeriodHistoryRepository;
    private final CeremonyRepository ceremonyRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    @Transactional
    public BillingPlanDto.Response.BillingPlanSummary createPlan(
            String actingPlatformRole,
            Long adminUserId,
            BillingPlanDto.Request.CreatePlan request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        Map<CapacityType, Integer> capacities = resolveCapacities(request.getCapacities());

        List<Long> optionalFeatureIds = request.getOptionalFeatureIds() == null
                ? List.of()
                : request.getOptionalFeatureIds();
        List<OptionalFeature> optionalFeatures = resolveOptionalFeatures(optionalFeatureIds);

        List<Long> capacityAddOnIds = request.getCapacityAddOnIds() == null
                ? List.of()
                : request.getCapacityAddOnIds();
        List<CapacityAddOn> capacityAddOns = resolveCapacityAddOns(capacityAddOnIds);

        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        DiscountType discountType = parseDiscountType(request.getDiscountType());

        BillingPlan plan = BillingPlan.builder().name(request.getName()).build();
        billingPlanRepository.save(plan);
        saveCapacities(plan, capacities);
        recordPlanHistory(plan);

        BillingPlanPricePeriod period = BillingPlanPricePeriod.builder()
                .billingPlan(plan)
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(discountType)
                .discountValue(request.getDiscountValue())
                .taxCode(request.getTaxCode())
                .active(request.getActive())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();
        billingPlanPricePeriodRepository.save(period);
        recordPeriodHistory(plan, period, false);

        for (OptionalFeature optionalFeature : optionalFeatures) {
            billingPlanOptionalFeatureRepository.save(
                    BillingPlanOptionalFeature.builder()
                            .billingPlan(plan)
                            .optionalFeature(optionalFeature)
                            .build()
            );
        }
        for (CapacityAddOn capacityAddOn : capacityAddOns) {
            billingPlanCapacityAddOnRepository.save(
                    BillingPlanCapacityAddOn.builder()
                            .billingPlan(plan)
                            .capacityAddOn(capacityAddOn)
                            .build()
            );
        }

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.CREATE_BILLING_PLAN,
                null,
                null,
                "planId=" + plan.getId() + ", name=" + plan.getName()
        );

        return toSummary(plan, capacities, optionalFeatureIds, capacityAddOnIds);
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

        Map<CapacityType, Integer> capacities = resolveCapacities(request.getCapacities());

        List<Long> optionalFeatureIds = request.getOptionalFeatureIds() == null
                ? List.of()
                : request.getOptionalFeatureIds();
        List<OptionalFeature> optionalFeatures = resolveOptionalFeatures(optionalFeatureIds);

        List<Long> capacityAddOnIds = request.getCapacityAddOnIds() == null
                ? List.of()
                : request.getCapacityAddOnIds();
        List<CapacityAddOn> capacityAddOns = resolveCapacityAddOns(capacityAddOnIds);

        String detail = "planId=" + planId
                + ", name: " + plan.getName() + " -> " + request.getName()
                + ", optionalFeatureIds: " + retrieveOptionalFeatureIds(plan.getId()) + " -> " + optionalFeatureIds
                + ", capacityAddOnIds: " + retrieveCapacityAddOnIds(plan.getId()) + " -> " + capacityAddOnIds;

        plan.updateInfo(request.getName());

        // 한도 구성 통째로 교체 — optionalFeatureIds/capacityAddOnIds와 같은 원칙(9장 후속 결정).
        // 이미 확정/진행 중인 Ceremony는 CeremonyPlanHistoryCapacity 스냅샷으로 보호되어
        // 이 변경에 영향받지 않는다.
        billingPlanCapacityRepository.deleteAllByBillingPlanId(planId);
        saveCapacities(plan, capacities);
        recordPlanHistory(plan);

        // 선택옵션 구성 통째로 교체 — 이미 확정/진행 중인 Ceremony는 CeremonyPlanHistoryOptionalFeature
        // 스냅샷으로 보호되어 이 변경에 영향받지 않는다(signstage-docs
        // business/ceremony-billing-options-review.md 9장 후속 결정).
        billingPlanOptionalFeatureRepository.deleteAllByBillingPlanId(planId);
        for (OptionalFeature optionalFeature : optionalFeatures) {
            billingPlanOptionalFeatureRepository.save(
                    BillingPlanOptionalFeature.builder().billingPlan(plan).optionalFeature(optionalFeature).build()
            );
        }

        // 구매 가능 용량 추가구매 상품 구성도 통째로 교체 — 같은 원칙으로
        // CeremonyPlanHistoryCapacityAddOn 스냅샷이 이미 진행 중인 Ceremony를 보호한다(signstage-docs
        // business/optional-feature-display-scope-and-plan-capacity-addon-review.md 5.5절).
        billingPlanCapacityAddOnRepository.deleteAllByBillingPlanId(planId);
        for (CapacityAddOn capacityAddOn : capacityAddOns) {
            billingPlanCapacityAddOnRepository.save(
                    BillingPlanCapacityAddOn.builder().billingPlan(plan).capacityAddOn(capacityAddOn).build()
            );
        }

        platformAdminAuditLogRecorder.record(adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null, detail);

        return toSummary(plan, capacities, optionalFeatureIds, capacityAddOnIds);
    }

    /** 새 판매가격 기간을 추가한다. */
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
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(planId, null, request.getEffectiveFrom(), request.getEffectiveTo());

        BillingPlanPricePeriod period = BillingPlanPricePeriod.builder()
                .billingPlan(plan)
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .taxCode(request.getTaxCode())
                .active(request.getActive())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();
        billingPlanPricePeriodRepository.save(period);
        recordPeriodHistory(plan, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null,
                "planId=" + planId + ", 판매가격 기간 생성: " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 이미 있는 판매가격 기간 하나를 고친다. */
    @Transactional
    public BillingPlanDto.Response.BillingPlanPeriodSummary updatePeriod(
            Long planId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            BillingPlanDto.Request.UpdatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        BillingPlan plan = billingPlanRepository.findById(planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        BillingPlanPricePeriod period = billingPlanPricePeriodRepository.findByIdAndBillingPlanId(periodId, planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(planId, periodId, request.getEffectiveFrom(), request.getEffectiveTo());

        String previous = describe(period);
        period.update(
                request.getCurrencyCode(), request.getSupplyPrice(), request.getSalePrice(),
                parseDiscountType(request.getDiscountType()), request.getDiscountValue(), request.getTaxCode(),
                request.getActive(), request.getEffectiveFrom(), request.getEffectiveTo()
        );
        recordPeriodHistory(plan, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null,
                "planId=" + planId + ", periodId=" + periodId + ", 판매가격 기간: " + previous + " -> " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 판매가격 기간을 제거한다 — 마지막 남은 기간 하나는 지울 수 없다(플랜은 항상 최소 1개 기간이 있어야 한다). */
    @Transactional
    public void removePeriod(Long planId, Long periodId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        billingPlanRepository.findById(planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        BillingPlanPricePeriod period = billingPlanPricePeriodRepository.findByIdAndBillingPlanId(periodId, planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        if (billingPlanPricePeriodRepository.countByBillingPlanId(planId) <= 1) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_REQUIRED);
        }

        recordPeriodHistory(period.getBillingPlan(), period, true);
        billingPlanPricePeriodRepository.delete(period);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null,
                "planId=" + planId + ", periodId=" + periodId + ", 판매가격 기간 제거: " + describe(period)
        );
    }

    /** 이 플랜의 판매가격 기간 전체(과거/현재/예정) — 오래된 순. */
    public List<BillingPlanDto.Response.BillingPlanPeriodSummary> findPlanPeriods(Long planId) {
        if (!billingPlanRepository.existsById(planId)) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND);
        }
        return billingPlanPricePeriodRepository.findAllByBillingPlanIdOrderByEffectiveFromAsc(planId).stream()
                .map(this::toPeriodSummary)
                .toList();
    }

    /** 판매가격 기간의 생성/수정/삭제 이력 — 최신순. */
    public List<BillingPlanDto.Response.BillingPlanPeriodHistorySummary> findPlanPeriodHistory(Long planId) {
        if (!billingPlanRepository.existsById(planId)) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND);
        }
        return billingPlanPricePeriodHistoryRepository.findAllByBillingPlanIdOrderByCreatedAtDesc(planId).stream()
                .map(h -> new BillingPlanDto.Response.BillingPlanPeriodHistorySummary(
                        h.getId(),
                        h.getPriceInfo().getCurrencyCode(),
                        h.getPriceInfo().getSupplyPrice(),
                        h.getPriceInfo().getSalePrice(),
                        h.getPriceInfo().getDiscount().getDiscountType().name(),
                        h.getPriceInfo().getDiscount().getDiscountValue(),
                        h.getPriceInfo().getTaxCode(),
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
                .map(plan -> toSummary(
                        plan,
                        retrieveCapacities(plan.getId()),
                        retrieveOptionalFeatureIds(plan.getId()),
                        retrieveCapacityAddOnIds(plan.getId())
                ))
                .toList();
    }

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(이름 또는 한도 구성이 바뀔 때). */
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
        billingPlanCapacityRepository.findAllByBillingPlanId(plan.getId()).forEach(capacity ->
                billingPlanHistoryCapacityRepository.save(
                        BillingPlanHistoryCapacity.builder()
                                .billingPlanHistory(history)
                                .capacityType(capacity.getCapacityType())
                                .includedAmount(capacity.getIncludedAmount())
                                .build()
                )
        );
    }

    /** 판매가격 기간 설정(생성/수정) 시점마다, 그리고 {@link #removePeriod}에서 제거 시점마다 호출한다. */
    private void recordPeriodHistory(BillingPlan plan, BillingPlanPricePeriod period, boolean removed) {
        billingPlanPricePeriodHistoryRepository.save(
                BillingPlanPricePeriodHistory.builder().billingPlan(plan).period(period).removed(removed).build()
        );
    }

    private void saveCapacities(BillingPlan plan, Map<CapacityType, Integer> capacities) {
        capacities.forEach((type, amount) ->
                billingPlanCapacityRepository.save(
                        BillingPlanCapacity.builder().billingPlan(plan).capacityType(type).includedAmount(amount).build()
                )
        );
    }

    /**
     * 요청 맵을 검증해 {@code CapacityType} 키로 정규화한다 — signstage-docs
     * business/billing-catalog-zero-base-schema-redesign-review.md 결정 #2(2026-09-08):
     * 정확히 {@code CapacityType.planIncludableTypes()}와 같은 키 집합이어야 하고(누락/여분 모두
     * 거부), 값은 0 이상이어야 한다. DB의 NOT NULL 컬럼 5개가 하던 "누락 방지" 역할을 이제
     * 애플리케이션 검증이 대신한다.
     */
    private Map<CapacityType, Integer> resolveCapacities(Map<String, Integer> capacities) {
        if (capacities == null) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        Map<CapacityType, Integer> resolved = new EnumMap<>(CapacityType.class);
        for (Map.Entry<String, Integer> entry : capacities.entrySet()) {
            CapacityType type;
            try {
                type = CapacityType.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
            if (!type.isPlanIncludable()) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
            Integer amount = entry.getValue();
            if (amount == null || amount < 0) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
            resolved.put(type, amount);
        }
        if (!resolved.keySet().equals(CapacityType.planIncludableTypes())) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        return resolved;
    }

    private Map<CapacityType, Integer> retrieveCapacities(Long billingPlanId) {
        return billingPlanCapacityRepository.findAllByBillingPlanId(billingPlanId).stream()
                .collect(Collectors.toMap(BillingPlanCapacity::getCapacityType, BillingPlanCapacity::getIncludedAmount));
    }

    private List<OptionalFeature> resolveOptionalFeatures(List<Long> optionalFeatureIds) {
        if (CollectionUtils.isEmpty(optionalFeatureIds)) {
            return List.of();
        }
        List<OptionalFeature> found = optionalFeatureRepository.findAllByIdIn(optionalFeatureIds);
        if (found.size() != optionalFeatureIds.size()) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND);
        }
        return found;
    }

    private List<Long> retrieveOptionalFeatureIds(Long billingPlanId) {
        return billingPlanOptionalFeatureRepository.findAllByBillingPlanId(billingPlanId).stream()
                .map(mapping -> mapping.getOptionalFeature().getId())
                .toList();
    }

    private List<CapacityAddOn> resolveCapacityAddOns(List<Long> capacityAddOnIds) {
        if (CollectionUtils.isEmpty(capacityAddOnIds)) {
            return List.of();
        }
        List<CapacityAddOn> found = capacityAddOnRepository.findAllByIdIn(capacityAddOnIds);
        if (found.size() != capacityAddOnIds.size()) {
            throw new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND);
        }
        return found;
    }

    private List<Long> retrieveCapacityAddOnIds(Long billingPlanId) {
        return billingPlanCapacityAddOnRepository.findAllByBillingPlanId(billingPlanId).stream()
                .map(mapping -> mapping.getCapacityAddOn().getId())
                .toList();
    }

    private DiscountType parseDiscountType(String discountType) {
        try {
            return DiscountType.valueOf(discountType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private void checkPeriodValid(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_PERIOD_INVALID);
        }
    }

    private void checkNoOverlap(Long planId, Long excludePeriodId, LocalDate newFrom, LocalDate newTo) {
        boolean overlaps = billingPlanPricePeriodRepository.findAllByBillingPlanIdOrderByEffectiveFromAsc(planId).stream()
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
     * 지남)/INACTIVE(기간 안이지만 active=false) — 관리 화면 배지용. "오늘"의 타임존은 조직별
     * 할인 오버라이드 문서의 결정 #5가 유보라 서버 기본 타임존(JVM LocalDate.now())을 잠정적으로
     * 쓴다(표시 전용 값).
     */
    private String computeStatus(boolean active, LocalDate effectiveFrom, LocalDate effectiveTo) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(effectiveFrom)) {
            return "PENDING";
        }
        if (effectiveTo != null && today.isAfter(effectiveTo)) {
            return "EXPIRED";
        }
        return active ? "ON_SALE" : "INACTIVE";
    }

    private String describe(BillingPlanPricePeriod period) {
        return period.getPriceInfo().getSalePrice() + " " + period.getPriceInfo().getCurrencyCode()
                + " (" + period.getEffectiveFrom() + " ~ " + (period.getEffectiveTo() == null ? "무기한" : period.getEffectiveTo()) + ")";
    }

    private BillingPlanDto.Response.BillingPlanSummary toSummary(
            BillingPlan plan,
            Map<CapacityType, Integer> capacities,
            List<Long> optionalFeatureIds,
            List<Long> capacityAddOnIds
    ) {
        Map<String, Integer> capacitiesResponse = capacities.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        Optional<BillingPlanPricePeriod> effective = billingPlanPricePeriodRepository.findEffective(plan.getId(), LocalDate.now());
        return new BillingPlanDto.Response.BillingPlanSummary(
                plan.getId(),
                plan.getName(),
                capacitiesResponse,
                optionalFeatureIds,
                capacityAddOnIds,
                ceremonyRepository.countByBillingPlanId(plan.getId()),
                plan.getCreatedAt(),
                effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSupplyPrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getDiscount().getDiscountType().name()).orElse(null),
                effective.map(p -> p.getPriceInfo().getDiscount().getDiscountValue()).orElse(null),
                effective.map(p -> p.getPriceInfo().getTaxCode()).orElse(null),
                effective.map(BillingPlanPricePeriod::isActive).orElse(null),
                effective.map(BillingPlanPricePeriod::getEffectiveFrom).orElse(null),
                effective.map(BillingPlanPricePeriod::getEffectiveTo).orElse(null),
                effective.map(p -> computeStatus(p.isActive(), p.getEffectiveFrom(), p.getEffectiveTo())).orElse("NO_ACTIVE_PERIOD")
        );
    }

    private BillingPlanDto.Response.BillingPlanHistorySummary toHistorySummary(BillingPlanHistory history) {
        Map<String, Integer> capacities = billingPlanHistoryCapacityRepository.findAllByBillingPlanHistoryId(history.getId()).stream()
                .collect(Collectors.toMap(c -> c.getCapacityType().name(), BillingPlanHistoryCapacity::getIncludedAmount));
        return new BillingPlanDto.Response.BillingPlanHistorySummary(
                history.getId(),
                history.getName(),
                capacities,
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private BillingPlanDto.Response.BillingPlanPeriodSummary toPeriodSummary(BillingPlanPricePeriod period) {
        return new BillingPlanDto.Response.BillingPlanPeriodSummary(
                period.getId(),
                period.getPriceInfo().getCurrencyCode(),
                period.getPriceInfo().getSupplyPrice(),
                period.getPriceInfo().getSalePrice(),
                period.getPriceInfo().getDiscount().getDiscountType().name(),
                period.getPriceInfo().getDiscount().getDiscountValue(),
                period.getPriceInfo().getTaxCode(),
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
