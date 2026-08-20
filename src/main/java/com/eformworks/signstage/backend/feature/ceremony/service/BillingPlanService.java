package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.util.List;
import java.util.Set;
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

    private static final Set<String> CATALOG_MANAGE_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final BillingPlanRepository billingPlanRepository;
    private final OptionalFeatureRepository optionalFeatureRepository;
    private final BillingPlanOptionalFeatureRepository billingPlanOptionalFeatureRepository;
    private final BillingPlanHistoryRepository billingPlanHistoryRepository;
    private final CeremonyRepository ceremonyRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    @Transactional
    public BillingPlanDto.Response.BillingPlanSummary createPlan(
            String actingPlatformRole,
            Long adminUserId,
            BillingPlanDto.Request.CreatePlan request
    ) {
        if (!CATALOG_MANAGE_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        List<Long> optionalFeatureIds = request.getOptionalFeatureIds() == null
                ? List.of()
                : request.getOptionalFeatureIds();
        List<OptionalFeature> optionalFeatures = resolveOptionalFeatures(optionalFeatureIds);

        BillingPlan plan = BillingPlan.builder()
                .name(request.getName())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .discountType(parseDiscountType(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .maxSigners(request.getMaxSigners())
                .maxTemplates(request.getMaxTemplates())
                .maxTestEvents(request.getMaxTestEvents())
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

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.CREATE_BILLING_PLAN,
                null,
                null,
                "planId=" + plan.getId() + ", name=" + plan.getName()
        );

        return toSummary(plan, optionalFeatureIds);
    }

    @Transactional
    public BillingPlanDto.Response.BillingPlanSummary updatePlan(
            Long planId,
            String actingPlatformRole,
            Long adminUserId,
            BillingPlanDto.Request.UpdatePlan request
    ) {
        if (!CATALOG_MANAGE_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        BillingPlan plan = billingPlanRepository.findById(planId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));

        List<Long> optionalFeatureIds = request.getOptionalFeatureIds() == null
                ? List.of()
                : request.getOptionalFeatureIds();
        List<OptionalFeature> optionalFeatures = resolveOptionalFeatures(optionalFeatureIds);

        String detail = "planId=" + planId
                + ", salePrice: " + plan.getSalePrice() + " -> " + request.getSalePrice()
                + ", active: " + plan.isActive() + " -> " + request.getActive()
                + ", optionalFeatureIds: " + retrieveOptionalFeatureIds(plan.getId()) + " -> " + optionalFeatureIds;

        plan.updateInfo(
                request.getName(),
                request.getSupplyPrice(),
                request.getSalePrice(),
                parseDiscountType(request.getDiscountType()),
                request.getDiscountValue(),
                request.getMaxSigners(),
                request.getMaxTemplates(),
                request.getMaxTestEvents(),
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

        platformAdminAuditLogRecorder.record(adminUserId, PlatformAdminAction.UPDATE_BILLING_PLAN, null, null, detail);

        return toSummary(plan, optionalFeatureIds);
    }

    public List<BillingPlanDto.Response.BillingPlanSummary> findPlans() {
        return billingPlanRepository.findAll().stream()
                .map(plan -> toSummary(plan, retrieveOptionalFeatureIds(plan.getId())))
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

    private DiscountType parseDiscountType(String discountType) {
        try {
            return DiscountType.valueOf(discountType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private BillingPlanDto.Response.BillingPlanSummary toSummary(BillingPlan plan, List<Long> optionalFeatureIds) {
        return new BillingPlanDto.Response.BillingPlanSummary(
                plan.getId(),
                plan.getName(),
                plan.getSupplyPrice(),
                plan.getSalePrice(),
                plan.getDiscountType().name(),
                plan.getDiscountValue(),
                plan.getMaxSigners(),
                plan.getMaxTemplates(),
                plan.getMaxTestEvents(),
                plan.getMaxMainEvents(),
                plan.isActive(),
                optionalFeatureIds,
                ceremonyRepository.countByBillingPlanId(plan.getId()),
                plan.getCreatedAt()
        );
    }

    private BillingPlanDto.Response.BillingPlanHistorySummary toHistorySummary(BillingPlanHistory history) {
        return new BillingPlanDto.Response.BillingPlanHistorySummary(
                history.getId(),
                history.getName(),
                history.getSupplyPrice(),
                history.getSalePrice(),
                history.getDiscountType().name(),
                history.getDiscountValue(),
                history.getMaxSigners(),
                history.getMaxTemplates(),
                history.getMaxTestEvents(),
                history.getMaxMainEvents(),
                history.isActive(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }
}
