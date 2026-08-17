package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
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

    @Transactional
    public BillingPlanDto.Response.BillingPlanSummary createPlan(
            String actingPlatformRole,
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

        for (OptionalFeature optionalFeature : optionalFeatures) {
            billingPlanOptionalFeatureRepository.save(
                    BillingPlanOptionalFeature.builder()
                            .billingPlan(plan)
                            .optionalFeature(optionalFeature)
                            .build()
            );
        }

        return toSummary(plan, optionalFeatureIds);
    }

    public List<BillingPlanDto.Response.BillingPlanSummary> findPlans() {
        return billingPlanRepository.findAll().stream()
                .map(plan -> toSummary(plan, retrieveOptionalFeatureIds(plan.getId())))
                .toList();
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
                optionalFeatureIds,
                plan.getCreatedAt()
        );
    }
}
