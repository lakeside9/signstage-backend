package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.OrganizationDiscountDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscount;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscountHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationCapacityAddOnDiscount;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationCapacityAddOnDiscountHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationOptionalFeatureDiscount;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationOptionalFeatureDiscountHistory;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationCapacityAddOnDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationCapacityAddOnDiscountRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationOptionalFeatureDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationOptionalFeatureDiscountRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직×품목 세밀 할인 오버라이드(안 A). signstage-docs
 * business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토: 조직 전역
 * 할인은 보류하고 조직×품목 오버라이드로 재추진) 참고.
 *
 * <p>등록/수정/삭제는 카탈로그 관리와 같은 기준(PLATFORM_OPS 이상)이다({@link BillingPlanService}
 * 등과 같은 패턴). 이 값은 {@code CeremonyService}가 Ceremony 생성(플랜)·구매 요청(선택옵션/
 * 용량 추가구매) 시점에 각 스냅샷 컬럼으로 한 번만 복사해 가므로, 여기 값을 나중에 바꿔도
 * 이미 만들어진 Ceremony/구매 건에는 영향을 주지 않는다(라이브 참조가 아니라 스냅샷 고정 —
 * 같은 문서 4.1절 결정).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationDiscountService {

    private static final Set<String> CATALOG_MANAGE_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final OrganizationRepository organizationRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final OptionalFeatureRepository optionalFeatureRepository;
    private final CapacityAddOnRepository capacityAddOnRepository;
    private final OrganizationBillingPlanDiscountRepository organizationBillingPlanDiscountRepository;
    private final OrganizationOptionalFeatureDiscountRepository organizationOptionalFeatureDiscountRepository;
    private final OrganizationCapacityAddOnDiscountRepository organizationCapacityAddOnDiscountRepository;
    private final OrganizationBillingPlanDiscountHistoryRepository organizationBillingPlanDiscountHistoryRepository;
    private final OrganizationOptionalFeatureDiscountHistoryRepository organizationOptionalFeatureDiscountHistoryRepository;
    private final OrganizationCapacityAddOnDiscountHistoryRepository organizationCapacityAddOnDiscountHistoryRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    // ---- 관리자 CRUD ----

    @Transactional
    public OrganizationDiscountDto.Response.BillingPlanDiscountSummary setBillingPlanDiscount(
            Long organizationId,
            Long billingPlanId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        requireCatalogManageRole(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        BillingPlan plan = billingPlanRepository.findById(billingPlanId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        DiscountType newType = parseDiscountType(request.getDiscountType());

        OrganizationBillingPlanDiscount override = organizationBillingPlanDiscountRepository
                .findByOrganizationIdAndBillingPlanId(organizationId, billingPlanId)
                .orElse(null);
        String previous = describe(override == null ? null : override.getDiscountType(), override == null ? null : override.getDiscountValue());
        if (override == null) {
            override = OrganizationBillingPlanDiscount.builder()
                    .organization(organization)
                    .billingPlan(plan)
                    .discountType(newType)
                    .discountValue(request.getDiscountValue())
                    .build();
        } else {
            override.update(newType, request.getDiscountValue());
        }
        organizationBillingPlanDiscountRepository.save(override);
        recordBillingPlanDiscountHistory(organization, plan, newType, request.getDiscountValue(), false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_BILLING_PLAN_DISCOUNT, null, organizationId,
                "billingPlanId=" + billingPlanId + ", discount: " + previous + " -> " + describe(newType, request.getDiscountValue())
        );

        return toBillingPlanDiscountSummary(override);
    }

    @Transactional
    public void removeBillingPlanDiscount(Long organizationId, Long billingPlanId, String actingPlatformRole, Long adminUserId) {
        requireCatalogManageRole(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);

        organizationBillingPlanDiscountRepository.findByOrganizationIdAndBillingPlanId(organizationId, billingPlanId)
                .ifPresent(override -> {
                    String previous = describe(override.getDiscountType(), override.getDiscountValue());
                    recordBillingPlanDiscountHistory(
                            organization, override.getBillingPlan(), override.getDiscountType(), override.getDiscountValue(), true
                    );
                    organizationBillingPlanDiscountRepository.delete(override);
                    platformAdminAuditLogRecorder.record(
                            adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_BILLING_PLAN_DISCOUNT, null, organizationId,
                            "billingPlanId=" + billingPlanId + ", discount: " + previous + " -> 오버라이드 제거(카탈로그 값 사용)"
                    );
                });
    }

    /** 최신순 — 설정(생성/수정) 시점마다, 그리고 제거 시점에(removed=true) 한 건씩 쌓인 이력. */
    public List<OrganizationDiscountDto.Response.BillingPlanDiscountHistorySummary> findBillingPlanDiscountHistory(
            Long organizationId, Long billingPlanId
    ) {
        findOrganizationOrThrow(organizationId);
        if (!billingPlanRepository.existsById(billingPlanId)) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND);
        }
        return organizationBillingPlanDiscountHistoryRepository
                .findAllByOrganizationIdAndBillingPlanIdOrderByCreatedAtDesc(organizationId, billingPlanId).stream()
                .map(this::toBillingPlanDiscountHistorySummary)
                .toList();
    }

    @Transactional
    public OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary setOptionalFeatureDiscount(
            Long organizationId,
            Long optionalFeatureId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        requireCatalogManageRole(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        OptionalFeature feature = optionalFeatureRepository.findById(optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));
        DiscountType newType = parseDiscountType(request.getDiscountType());

        OrganizationOptionalFeatureDiscount override = organizationOptionalFeatureDiscountRepository
                .findByOrganizationIdAndOptionalFeatureId(organizationId, optionalFeatureId)
                .orElse(null);
        String previous = describe(override == null ? null : override.getDiscountType(), override == null ? null : override.getDiscountValue());
        if (override == null) {
            override = OrganizationOptionalFeatureDiscount.builder()
                    .organization(organization)
                    .optionalFeature(feature)
                    .discountType(newType)
                    .discountValue(request.getDiscountValue())
                    .build();
        } else {
            override.update(newType, request.getDiscountValue());
        }
        organizationOptionalFeatureDiscountRepository.save(override);
        recordOptionalFeatureDiscountHistory(organization, feature, newType, request.getDiscountValue(), false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_OPTIONAL_FEATURE_DISCOUNT, null, organizationId,
                "optionalFeatureId=" + optionalFeatureId + ", discount: " + previous + " -> " + describe(newType, request.getDiscountValue())
        );

        return toOptionalFeatureDiscountSummary(override);
    }

    @Transactional
    public void removeOptionalFeatureDiscount(Long organizationId, Long optionalFeatureId, String actingPlatformRole, Long adminUserId) {
        requireCatalogManageRole(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);

        organizationOptionalFeatureDiscountRepository.findByOrganizationIdAndOptionalFeatureId(organizationId, optionalFeatureId)
                .ifPresent(override -> {
                    String previous = describe(override.getDiscountType(), override.getDiscountValue());
                    recordOptionalFeatureDiscountHistory(
                            organization, override.getOptionalFeature(), override.getDiscountType(), override.getDiscountValue(), true
                    );
                    organizationOptionalFeatureDiscountRepository.delete(override);
                    platformAdminAuditLogRecorder.record(
                            adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_OPTIONAL_FEATURE_DISCOUNT, null, organizationId,
                            "optionalFeatureId=" + optionalFeatureId + ", discount: " + previous + " -> 오버라이드 제거(카탈로그 값 사용)"
                    );
                });
    }

    public List<OrganizationDiscountDto.Response.OptionalFeatureDiscountHistorySummary> findOptionalFeatureDiscountHistory(
            Long organizationId, Long optionalFeatureId
    ) {
        findOrganizationOrThrow(organizationId);
        if (!optionalFeatureRepository.existsById(optionalFeatureId)) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND);
        }
        return organizationOptionalFeatureDiscountHistoryRepository
                .findAllByOrganizationIdAndOptionalFeatureIdOrderByCreatedAtDesc(organizationId, optionalFeatureId).stream()
                .map(this::toOptionalFeatureDiscountHistorySummary)
                .toList();
    }

    @Transactional
    public OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary setCapacityAddOnDiscount(
            Long organizationId,
            Long capacityAddOnId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        requireCatalogManageRole(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        CapacityAddOn addOn = capacityAddOnRepository.findById(capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));
        DiscountType newType = parseDiscountType(request.getDiscountType());

        OrganizationCapacityAddOnDiscount override = organizationCapacityAddOnDiscountRepository
                .findByOrganizationIdAndCapacityAddOnId(organizationId, capacityAddOnId)
                .orElse(null);
        String previous = describe(override == null ? null : override.getDiscountType(), override == null ? null : override.getDiscountValue());
        if (override == null) {
            override = OrganizationCapacityAddOnDiscount.builder()
                    .organization(organization)
                    .capacityAddOn(addOn)
                    .discountType(newType)
                    .discountValue(request.getDiscountValue())
                    .build();
        } else {
            override.update(newType, request.getDiscountValue());
        }
        organizationCapacityAddOnDiscountRepository.save(override);
        recordCapacityAddOnDiscountHistory(organization, addOn, newType, request.getDiscountValue(), false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_CAPACITY_ADDON_DISCOUNT, null, organizationId,
                "capacityAddOnId=" + capacityAddOnId + ", discount: " + previous + " -> " + describe(newType, request.getDiscountValue())
        );

        return toCapacityAddOnDiscountSummary(override);
    }

    @Transactional
    public void removeCapacityAddOnDiscount(Long organizationId, Long capacityAddOnId, String actingPlatformRole, Long adminUserId) {
        requireCatalogManageRole(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);

        organizationCapacityAddOnDiscountRepository.findByOrganizationIdAndCapacityAddOnId(organizationId, capacityAddOnId)
                .ifPresent(override -> {
                    String previous = describe(override.getDiscountType(), override.getDiscountValue());
                    recordCapacityAddOnDiscountHistory(
                            organization, override.getCapacityAddOn(), override.getDiscountType(), override.getDiscountValue(), true
                    );
                    organizationCapacityAddOnDiscountRepository.delete(override);
                    platformAdminAuditLogRecorder.record(
                            adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_CAPACITY_ADDON_DISCOUNT, null, organizationId,
                            "capacityAddOnId=" + capacityAddOnId + ", discount: " + previous + " -> 오버라이드 제거(카탈로그 값 사용)"
                    );
                });
    }

    public List<OrganizationDiscountDto.Response.CapacityAddOnDiscountHistorySummary> findCapacityAddOnDiscountHistory(
            Long organizationId, Long capacityAddOnId
    ) {
        findOrganizationOrThrow(organizationId);
        if (!capacityAddOnRepository.existsById(capacityAddOnId)) {
            throw new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND);
        }
        return organizationCapacityAddOnDiscountHistoryRepository
                .findAllByOrganizationIdAndCapacityAddOnIdOrderByCreatedAtDesc(organizationId, capacityAddOnId).stream()
                .map(this::toCapacityAddOnDiscountHistorySummary)
                .toList();
    }

    /** 조직별 할인 관리 화면 — 이 조직에 걸린 세 카탈로그 종류의 오버라이드를 한 번에 보여준다. */
    public OrganizationDiscountDto.Response.OrganizationDiscountOverview findDiscounts(Long organizationId) {
        findOrganizationOrThrow(organizationId);

        return new OrganizationDiscountDto.Response.OrganizationDiscountOverview(
                organizationBillingPlanDiscountRepository.findAllByOrganizationId(organizationId).stream()
                        .map(this::toBillingPlanDiscountSummary).toList(),
                organizationOptionalFeatureDiscountRepository.findAllByOrganizationId(organizationId).stream()
                        .map(this::toOptionalFeatureDiscountSummary).toList(),
                organizationCapacityAddOnDiscountRepository.findAllByOrganizationId(organizationId).stream()
                        .map(this::toCapacityAddOnDiscountSummary).toList()
        );
    }

    // ---- CeremonyService가 스냅샷 시점(플랜 선택/구매 요청)에 쓰는 해석 로직 ----
    // 같은 패키지(feature.ceremony.service) 안에서만 쓰는 package-private 헬퍼다 — 조직/행사
    // 접근 검사와 유효 한도 계산을 CeremonyService의 package-private 헬퍼로 재사용하는 것과
    // 같은 관례(CeremonyEventService 문서 주석 참고).

    /** 오버라이드가 있으면 그 값, 없으면 카탈로그({@code plan}) 자체의 할인값. */
    EffectiveDiscount resolveBillingPlanDiscount(Organization organization, BillingPlan plan) {
        return organizationBillingPlanDiscountRepository
                .findByOrganizationIdAndBillingPlanId(organization.getId(), plan.getId())
                .map(override -> new EffectiveDiscount(override.getDiscountType(), override.getDiscountValue()))
                .orElseGet(() -> new EffectiveDiscount(plan.getDiscountType(), plan.getDiscountValue()));
    }

    EffectiveDiscount resolveOptionalFeatureDiscount(Organization organization, OptionalFeature feature) {
        return organizationOptionalFeatureDiscountRepository
                .findByOrganizationIdAndOptionalFeatureId(organization.getId(), feature.getId())
                .map(override -> new EffectiveDiscount(override.getDiscountType(), override.getDiscountValue()))
                .orElseGet(() -> new EffectiveDiscount(feature.getDiscountType(), feature.getDiscountValue()));
    }

    EffectiveDiscount resolveCapacityAddOnDiscount(Organization organization, CapacityAddOn addOn) {
        return organizationCapacityAddOnDiscountRepository
                .findByOrganizationIdAndCapacityAddOnId(organization.getId(), addOn.getId())
                .map(override -> new EffectiveDiscount(override.getDiscountType(), override.getDiscountValue()))
                .orElseGet(() -> new EffectiveDiscount(addOn.getDiscountType(), addOn.getDiscountValue()));
    }

    /** {@code CeremonyService}가 스냅샷 컬럼에 그대로 옮겨 담는 해석 결과 값 객체. */
    record EffectiveDiscount(DiscountType type, BigDecimal value) {
    }

    private void requireCatalogManageRole(String actingPlatformRole) {
        if (!CATALOG_MANAGE_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private DiscountType parseDiscountType(String discountType) {
        try {
            return DiscountType.valueOf(discountType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private String describe(DiscountType type, BigDecimal value) {
        return type == null ? "없음(카탈로그 값 사용)" : type + " " + value;
    }

    /** 설정(생성/수정) 시점마다, 그리고 {@code removeXxxDiscount}에서 제거 시점마다 호출한다. */
    private void recordBillingPlanDiscountHistory(
            Organization organization, BillingPlan plan, DiscountType discountType, BigDecimal discountValue, boolean removed
    ) {
        organizationBillingPlanDiscountHistoryRepository.save(
                OrganizationBillingPlanDiscountHistory.builder()
                        .organization(organization)
                        .billingPlan(plan)
                        .discountType(discountType)
                        .discountValue(discountValue)
                        .removed(removed)
                        .build()
        );
    }

    private void recordOptionalFeatureDiscountHistory(
            Organization organization, OptionalFeature feature, DiscountType discountType, BigDecimal discountValue, boolean removed
    ) {
        organizationOptionalFeatureDiscountHistoryRepository.save(
                OrganizationOptionalFeatureDiscountHistory.builder()
                        .organization(organization)
                        .optionalFeature(feature)
                        .discountType(discountType)
                        .discountValue(discountValue)
                        .removed(removed)
                        .build()
        );
    }

    private void recordCapacityAddOnDiscountHistory(
            Organization organization, CapacityAddOn addOn, DiscountType discountType, BigDecimal discountValue, boolean removed
    ) {
        organizationCapacityAddOnDiscountHistoryRepository.save(
                OrganizationCapacityAddOnDiscountHistory.builder()
                        .organization(organization)
                        .capacityAddOn(addOn)
                        .discountType(discountType)
                        .discountValue(discountValue)
                        .removed(removed)
                        .build()
        );
    }

    private OrganizationDiscountDto.Response.BillingPlanDiscountSummary toBillingPlanDiscountSummary(OrganizationBillingPlanDiscount override) {
        return new OrganizationDiscountDto.Response.BillingPlanDiscountSummary(
                override.getId(),
                override.getOrganization().getId(),
                override.getBillingPlan().getId(),
                override.getBillingPlan().getName(),
                override.getDiscountType().name(),
                override.getDiscountValue(),
                override.getCreatedAt()
        );
    }

    private OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary toOptionalFeatureDiscountSummary(
            OrganizationOptionalFeatureDiscount override
    ) {
        return new OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary(
                override.getId(),
                override.getOrganization().getId(),
                override.getOptionalFeature().getId(),
                override.getOptionalFeature().getName(),
                override.getDiscountType().name(),
                override.getDiscountValue(),
                override.getCreatedAt()
        );
    }

    private OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary toCapacityAddOnDiscountSummary(
            OrganizationCapacityAddOnDiscount override
    ) {
        return new OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary(
                override.getId(),
                override.getOrganization().getId(),
                override.getCapacityAddOn().getId(),
                override.getCapacityAddOn().getCapacityType().name(),
                override.getCapacityAddOn().getUnitAmount(),
                override.getDiscountType().name(),
                override.getDiscountValue(),
                override.getCreatedAt()
        );
    }

    private OrganizationDiscountDto.Response.BillingPlanDiscountHistorySummary toBillingPlanDiscountHistorySummary(
            OrganizationBillingPlanDiscountHistory history
    ) {
        return new OrganizationDiscountDto.Response.BillingPlanDiscountHistorySummary(
                history.getId(),
                history.getOrganization().getId(),
                history.getBillingPlan().getId(),
                history.getBillingPlan().getName(),
                history.getDiscountType().name(),
                history.getDiscountValue(),
                history.isRemoved(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private OrganizationDiscountDto.Response.OptionalFeatureDiscountHistorySummary toOptionalFeatureDiscountHistorySummary(
            OrganizationOptionalFeatureDiscountHistory history
    ) {
        return new OrganizationDiscountDto.Response.OptionalFeatureDiscountHistorySummary(
                history.getId(),
                history.getOrganization().getId(),
                history.getOptionalFeature().getId(),
                history.getOptionalFeature().getName(),
                history.getDiscountType().name(),
                history.getDiscountValue(),
                history.isRemoved(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private OrganizationDiscountDto.Response.CapacityAddOnDiscountHistorySummary toCapacityAddOnDiscountHistorySummary(
            OrganizationCapacityAddOnDiscountHistory history
    ) {
        return new OrganizationDiscountDto.Response.CapacityAddOnDiscountHistorySummary(
                history.getId(),
                history.getOrganization().getId(),
                history.getCapacityAddOn().getId(),
                history.getCapacityAddOn().getCapacityType().name(),
                history.getCapacityAddOn().getUnitAmount(),
                history.getDiscountType().name(),
                history.getDiscountValue(),
                history.isRemoved(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }
}
