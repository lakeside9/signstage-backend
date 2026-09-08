package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanCapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanCapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 행사(Ceremony) 과금 플랜 카탈로그. signstage-docs business/ceremony-billing-options-review.md
 * 4.2/4.9절 참고 — 필수옵션(서명자/템플릿/테스트·본행사 수 한도)은 모든 플랜이 항상 값을 가지며,
 * 그 값에 별도 시스템 상한을 코드로 두지 않는다(운영자가 카탈로그를 만들 때 정하는 값 그대로 쓴다).
 * 등록은 플랫폼 관리자 전용, 조회는 인증된 사용자 누구나 가능하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingPlanService {

    private final BillingPlanRepository billingPlanRepository;
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
                .maxSigners(request.getMaxSigners())
                .maxTemplates(request.getMaxTemplates())
                .maxTestEvents(request.getMaxTestEvents())
                .maxRehearsalEvents(request.getMaxRehearsalEvents())
                .maxMainEvents(request.getMaxMainEvents())
                .build();
        billingPlanRepository.save(plan);
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

        return toSummary(plan, optionalFeatureIds, capacityAddOnIds);
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

        List<Long> optionalFeatureIds = request.getOptionalFeatureIds() == null
                ? List.of()
                : request.getOptionalFeatureIds();
        List<OptionalFeature> optionalFeatures = resolveOptionalFeatures(optionalFeatureIds);

        List<Long> capacityAddOnIds = request.getCapacityAddOnIds() == null
                ? List.of()
                : request.getCapacityAddOnIds();
        List<CapacityAddOn> capacityAddOns = resolveCapacityAddOns(capacityAddOnIds);

        String detail = "planId=" + planId
                + ", salePrice: " + plan.getSalePrice() + " -> " + request.getSalePrice()
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
                request.getMaxSigners(),
                request.getMaxTemplates(),
                request.getMaxTestEvents(),
                request.getMaxRehearsalEvents(),
                request.getMaxMainEvents(),
                request.getActive()
        );
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

        return toSummary(plan, optionalFeatureIds, capacityAddOnIds);
    }

    public List<BillingPlanDto.Response.BillingPlanSummary> findPlans() {
        return billingPlanRepository.findAll().stream()
                .map(plan -> toSummary(plan, retrieveOptionalFeatureIds(plan.getId()), retrieveCapacityAddOnIds(plan.getId())))
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
        billingPlanHistoryRepository.save(BillingPlanHistory.builder().billingPlan(plan).build());
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
            List<Long> optionalFeatureIds,
            List<Long> capacityAddOnIds
    ) {
        return new BillingPlanDto.Response.BillingPlanSummary(
                plan.getId(),
                plan.getName(),
                plan.getCurrencyCode(),
                plan.getSupplyPrice(),
                plan.getSalePrice(),
                plan.getDiscountType().name(),
                plan.getDiscountValue(),
                plan.getTaxCode(),
                plan.getMaxSigners(),
                plan.getMaxTemplates(),
                plan.getMaxTestEvents(),
                plan.getMaxRehearsalEvents(),
                plan.getMaxMainEvents(),
                plan.isActive(),
                optionalFeatureIds,
                capacityAddOnIds,
                ceremonyRepository.countByBillingPlanId(plan.getId()),
                plan.getCreatedAt()
        );
    }

    private BillingPlanDto.Response.BillingPlanHistorySummary toHistorySummary(BillingPlanHistory history) {
        return new BillingPlanDto.Response.BillingPlanHistorySummary(
                history.getId(),
                history.getName(),
                history.getCurrencyCode(),
                history.getSupplyPrice(),
                history.getSalePrice(),
                history.getDiscountType().name(),
                history.getDiscountValue(),
                history.getTaxCode(),
                history.getMaxSigners(),
                history.getMaxTemplates(),
                history.getMaxTestEvents(),
                history.getMaxRehearsalEvents(),
                history.getMaxMainEvents(),
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
