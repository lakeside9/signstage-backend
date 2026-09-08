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
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

        BillingPlan plan = BillingPlan.builder()
                .name(request.getName())
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .taxCode(request.getTaxCode())
                .build();
        billingPlanRepository.save(plan);
        saveCapacities(plan, capacities);
        recordPlanHistory(plan);

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
                + ", salePrice: " + plan.getPriceInfo().getSalePrice() + " -> " + request.getSalePrice()
                + ", active: " + plan.isActive() + " -> " + request.getActive()
                + ", optionalFeatureIds: " + retrieveOptionalFeatureIds(plan.getId()) + " -> " + optionalFeatureIds
                + ", capacityAddOnIds: " + retrieveCapacityAddOnIds(plan.getId()) + " -> " + capacityAddOnIds;

        plan.updateInfo(
                request.getName(),
                request.getCurrencyCode(),
                request.getSupplyPrice(),
                request.getSalePrice(),
                parseDiscountType(request.getDiscountType()),
                request.getDiscountValue(),
                request.getTaxCode(),
                request.getActive()
        );

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

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(값 또는 사용여부가 바뀔 때). */
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

    private BillingPlanDto.Response.BillingPlanSummary toSummary(
            BillingPlan plan,
            Map<CapacityType, Integer> capacities,
            List<Long> optionalFeatureIds,
            List<Long> capacityAddOnIds
    ) {
        Map<String, Integer> capacitiesResponse = capacities.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        return new BillingPlanDto.Response.BillingPlanSummary(
                plan.getId(),
                plan.getName(),
                plan.getPriceInfo().getCurrencyCode(),
                plan.getPriceInfo().getSupplyPrice(),
                plan.getPriceInfo().getSalePrice(),
                plan.getPriceInfo().getDiscount().getDiscountType().name(),
                plan.getPriceInfo().getDiscount().getDiscountValue(),
                plan.getPriceInfo().getTaxCode(),
                capacitiesResponse,
                plan.isActive(),
                optionalFeatureIds,
                capacityAddOnIds,
                ceremonyRepository.countByBillingPlanId(plan.getId()),
                plan.getCreatedAt()
        );
    }

    private BillingPlanDto.Response.BillingPlanHistorySummary toHistorySummary(BillingPlanHistory history) {
        Map<String, Integer> capacities = billingPlanHistoryCapacityRepository.findAllByBillingPlanHistoryId(history.getId()).stream()
                .collect(Collectors.toMap(c -> c.getCapacityType().name(), BillingPlanHistoryCapacity::getIncludedAmount));
        return new BillingPlanDto.Response.BillingPlanHistorySummary(
                history.getId(),
                history.getName(),
                history.getPriceInfo().getCurrencyCode(),
                history.getPriceInfo().getSupplyPrice(),
                history.getPriceInfo().getSalePrice(),
                history.getPriceInfo().getDiscount().getDiscountType().name(),
                history.getPriceInfo().getDiscount().getDiscountValue(),
                history.getPriceInfo().getTaxCode(),
                capacities,
                history.isActive(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    /** signstage-docs business/menu-and-action-permission-management-review.md 10장 참고. */
    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
