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
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직×품목 세밀 할인 오버라이드(안 A). signstage-docs
 * business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토: 조직 전역
 * 할인은 보류하고 조직×품목 오버라이드로 재추진) 참고.
 *
 * <p>행 하나 = 기간 하나(다중 버전, {@code TaxPolicy}와 같은 방식)로 재설계됐다 — signstage-docs
 * business/organization-discount-override-security-and-validity-period-review.md 결정
 * #4(2026-09-08, 안 B 채택). 등록/수정/삭제는 카탈로그 관리와 같은 기준(동적 RBAC,
 * {@code ACTION_ORGANIZATION_DISCOUNT_MANAGE})이고, 기간이 겹치는 것은 DB 제약이 아니라
 * 이 서비스가 막는다(MySQL은 범위 제약을 지원하지 않는다 — 같은 문서 3.3절). 이 값은
 * {@code CeremonyService}가 Ceremony 생성(플랜)·구매 요청(선택옵션/용량 추가구매) 시점에
 * 각 스냅샷 컬럼으로 한 번만 복사해 가므로, 여기 값을 나중에 바꿔도 이미 만들어진
 * Ceremony/구매 건에는 영향을 주지 않는다(라이브 참조가 아니라 스냅샷 고정 — 같은 문서 4.1절
 * 결정).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationDiscountService {

    private static final String DISCOUNT_MANAGE_PERMISSION_KEY = "ACTION_ORGANIZATION_DISCOUNT_MANAGE";

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
    private final RolePermissionService rolePermissionService;

    // ---- 관리자 CRUD — 조직×플랜 ----

    @Transactional
    public OrganizationDiscountDto.Response.BillingPlanDiscountSummary createBillingPlanDiscountPeriod(
            Long organizationId,
            Long billingPlanId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        BillingPlan plan = billingPlanRepository.findById(billingPlanId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        DiscountType newType = parseDiscountType(request.getDiscountType());
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(
                organizationBillingPlanDiscountRepository.findAllByOrganizationIdAndBillingPlanIdOrderByEffectiveFromAsc(
                        organizationId, billingPlanId
                ).stream().map(d -> new PeriodRange(d.getId(), d.getEffectiveFrom(), d.getEffectiveTo())).toList(),
                null, request.getEffectiveFrom(), request.getEffectiveTo()
        );

        OrganizationBillingPlanDiscount period = OrganizationBillingPlanDiscount.builder()
                .organization(organization)
                .billingPlan(plan)
                .discountType(newType)
                .discountValue(request.getDiscountValue())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();
        organizationBillingPlanDiscountRepository.save(period);
        recordBillingPlanDiscountHistory(organization, plan, newType, request.getDiscountValue(), request.getEffectiveFrom(), request.getEffectiveTo(), false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_BILLING_PLAN_DISCOUNT, null, organizationId,
                "billingPlanId=" + billingPlanId + ", 기간 생성: " + describe(newType, request.getDiscountValue())
                        + " (" + request.getEffectiveFrom() + " ~ " + describeEnd(request.getEffectiveTo()) + ")"
        );

        return toBillingPlanDiscountSummary(period);
    }

    @Transactional
    public OrganizationDiscountDto.Response.BillingPlanDiscountSummary updateBillingPlanDiscountPeriod(
            Long organizationId,
            Long billingPlanId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        OrganizationBillingPlanDiscount period = findBillingPlanDiscountPeriodOrThrow(organizationId, billingPlanId, periodId);
        DiscountType newType = parseDiscountType(request.getDiscountType());
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(
                organizationBillingPlanDiscountRepository.findAllByOrganizationIdAndBillingPlanIdOrderByEffectiveFromAsc(
                        organizationId, billingPlanId
                ).stream().map(d -> new PeriodRange(d.getId(), d.getEffectiveFrom(), d.getEffectiveTo())).toList(),
                periodId, request.getEffectiveFrom(), request.getEffectiveTo()
        );
        String previous = describe(period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue());

        period.update(newType, request.getDiscountValue(), request.getEffectiveFrom(), request.getEffectiveTo());
        recordBillingPlanDiscountHistory(
                organization, period.getBillingPlan(), newType, request.getDiscountValue(),
                request.getEffectiveFrom(), request.getEffectiveTo(), false
        );

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_BILLING_PLAN_DISCOUNT, null, organizationId,
                "billingPlanId=" + billingPlanId + ", periodId=" + periodId + ", discount: " + previous
                        + " -> " + describe(newType, request.getDiscountValue())
        );

        return toBillingPlanDiscountSummary(period);
    }

    @Transactional
    public void removeBillingPlanDiscountPeriod(
            Long organizationId, Long billingPlanId, Long periodId, String actingPlatformRole, Long adminUserId
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        OrganizationBillingPlanDiscount period = findBillingPlanDiscountPeriodOrThrow(organizationId, billingPlanId, periodId);

        String previous = describe(period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue());
        recordBillingPlanDiscountHistory(
                organization, period.getBillingPlan(), period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue(),
                period.getEffectiveFrom(), period.getEffectiveTo(), true
        );
        organizationBillingPlanDiscountRepository.delete(period);
        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_BILLING_PLAN_DISCOUNT, null, organizationId,
                "billingPlanId=" + billingPlanId + ", periodId=" + periodId + ", discount: " + previous + " -> 기간 제거"
        );
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

    // ---- 관리자 CRUD — 조직×선택옵션 ----

    @Transactional
    public OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary createOptionalFeatureDiscountPeriod(
            Long organizationId,
            Long optionalFeatureId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        OptionalFeature feature = optionalFeatureRepository.findById(optionalFeatureId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));
        DiscountType newType = parseDiscountType(request.getDiscountType());
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(
                organizationOptionalFeatureDiscountRepository.findAllByOrganizationIdAndOptionalFeatureIdOrderByEffectiveFromAsc(
                        organizationId, optionalFeatureId
                ).stream().map(d -> new PeriodRange(d.getId(), d.getEffectiveFrom(), d.getEffectiveTo())).toList(),
                null, request.getEffectiveFrom(), request.getEffectiveTo()
        );

        OrganizationOptionalFeatureDiscount period = OrganizationOptionalFeatureDiscount.builder()
                .organization(organization)
                .optionalFeature(feature)
                .discountType(newType)
                .discountValue(request.getDiscountValue())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();
        organizationOptionalFeatureDiscountRepository.save(period);
        recordOptionalFeatureDiscountHistory(
                organization, feature, newType, request.getDiscountValue(), request.getEffectiveFrom(), request.getEffectiveTo(), false
        );

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_OPTIONAL_FEATURE_DISCOUNT, null, organizationId,
                "optionalFeatureId=" + optionalFeatureId + ", 기간 생성: " + describe(newType, request.getDiscountValue())
                        + " (" + request.getEffectiveFrom() + " ~ " + describeEnd(request.getEffectiveTo()) + ")"
        );

        return toOptionalFeatureDiscountSummary(period);
    }

    @Transactional
    public OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary updateOptionalFeatureDiscountPeriod(
            Long organizationId,
            Long optionalFeatureId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        OrganizationOptionalFeatureDiscount period = findOptionalFeatureDiscountPeriodOrThrow(organizationId, optionalFeatureId, periodId);
        DiscountType newType = parseDiscountType(request.getDiscountType());
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(
                organizationOptionalFeatureDiscountRepository.findAllByOrganizationIdAndOptionalFeatureIdOrderByEffectiveFromAsc(
                        organizationId, optionalFeatureId
                ).stream().map(d -> new PeriodRange(d.getId(), d.getEffectiveFrom(), d.getEffectiveTo())).toList(),
                periodId, request.getEffectiveFrom(), request.getEffectiveTo()
        );
        String previous = describe(period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue());

        period.update(newType, request.getDiscountValue(), request.getEffectiveFrom(), request.getEffectiveTo());
        recordOptionalFeatureDiscountHistory(
                organization, period.getOptionalFeature(), newType, request.getDiscountValue(),
                request.getEffectiveFrom(), request.getEffectiveTo(), false
        );

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_OPTIONAL_FEATURE_DISCOUNT, null, organizationId,
                "optionalFeatureId=" + optionalFeatureId + ", periodId=" + periodId + ", discount: " + previous
                        + " -> " + describe(newType, request.getDiscountValue())
        );

        return toOptionalFeatureDiscountSummary(period);
    }

    @Transactional
    public void removeOptionalFeatureDiscountPeriod(
            Long organizationId, Long optionalFeatureId, Long periodId, String actingPlatformRole, Long adminUserId
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        OrganizationOptionalFeatureDiscount period = findOptionalFeatureDiscountPeriodOrThrow(organizationId, optionalFeatureId, periodId);

        String previous = describe(period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue());
        recordOptionalFeatureDiscountHistory(
                organization, period.getOptionalFeature(), period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue(),
                period.getEffectiveFrom(), period.getEffectiveTo(), true
        );
        organizationOptionalFeatureDiscountRepository.delete(period);
        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_OPTIONAL_FEATURE_DISCOUNT, null, organizationId,
                "optionalFeatureId=" + optionalFeatureId + ", periodId=" + periodId + ", discount: " + previous + " -> 기간 제거"
        );
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

    // ---- 관리자 CRUD — 조직×용량 추가구매 ----

    @Transactional
    public OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary createCapacityAddOnDiscountPeriod(
            Long organizationId,
            Long capacityAddOnId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        CapacityAddOn addOn = capacityAddOnRepository.findById(capacityAddOnId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));
        DiscountType newType = parseDiscountType(request.getDiscountType());
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(
                organizationCapacityAddOnDiscountRepository.findAllByOrganizationIdAndCapacityAddOnIdOrderByEffectiveFromAsc(
                        organizationId, capacityAddOnId
                ).stream().map(d -> new PeriodRange(d.getId(), d.getEffectiveFrom(), d.getEffectiveTo())).toList(),
                null, request.getEffectiveFrom(), request.getEffectiveTo()
        );

        OrganizationCapacityAddOnDiscount period = OrganizationCapacityAddOnDiscount.builder()
                .organization(organization)
                .capacityAddOn(addOn)
                .discountType(newType)
                .discountValue(request.getDiscountValue())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();
        organizationCapacityAddOnDiscountRepository.save(period);
        recordCapacityAddOnDiscountHistory(
                organization, addOn, newType, request.getDiscountValue(), request.getEffectiveFrom(), request.getEffectiveTo(), false
        );

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_CAPACITY_ADDON_DISCOUNT, null, organizationId,
                "capacityAddOnId=" + capacityAddOnId + ", 기간 생성: " + describe(newType, request.getDiscountValue())
                        + " (" + request.getEffectiveFrom() + " ~ " + describeEnd(request.getEffectiveTo()) + ")"
        );

        return toCapacityAddOnDiscountSummary(period);
    }

    @Transactional
    public OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary updateCapacityAddOnDiscountPeriod(
            Long organizationId,
            Long capacityAddOnId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            OrganizationDiscountDto.Request.SetDiscount request
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        OrganizationCapacityAddOnDiscount period = findCapacityAddOnDiscountPeriodOrThrow(organizationId, capacityAddOnId, periodId);
        DiscountType newType = parseDiscountType(request.getDiscountType());
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(
                organizationCapacityAddOnDiscountRepository.findAllByOrganizationIdAndCapacityAddOnIdOrderByEffectiveFromAsc(
                        organizationId, capacityAddOnId
                ).stream().map(d -> new PeriodRange(d.getId(), d.getEffectiveFrom(), d.getEffectiveTo())).toList(),
                periodId, request.getEffectiveFrom(), request.getEffectiveTo()
        );
        String previous = describe(period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue());

        period.update(newType, request.getDiscountValue(), request.getEffectiveFrom(), request.getEffectiveTo());
        recordCapacityAddOnDiscountHistory(
                organization, period.getCapacityAddOn(), newType, request.getDiscountValue(),
                request.getEffectiveFrom(), request.getEffectiveTo(), false
        );

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_CAPACITY_ADDON_DISCOUNT, null, organizationId,
                "capacityAddOnId=" + capacityAddOnId + ", periodId=" + periodId + ", discount: " + previous
                        + " -> " + describe(newType, request.getDiscountValue())
        );

        return toCapacityAddOnDiscountSummary(period);
    }

    @Transactional
    public void removeCapacityAddOnDiscountPeriod(
            Long organizationId, Long capacityAddOnId, Long periodId, String actingPlatformRole, Long adminUserId
    ) {
        checkAllowed(actingPlatformRole);
        Organization organization = findOrganizationOrThrow(organizationId);
        OrganizationCapacityAddOnDiscount period = findCapacityAddOnDiscountPeriodOrThrow(organizationId, capacityAddOnId, periodId);

        String previous = describe(period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue());
        recordCapacityAddOnDiscountHistory(
                organization, period.getCapacityAddOn(), period.getDiscount().getDiscountType(), period.getDiscount().getDiscountValue(),
                period.getEffectiveFrom(), period.getEffectiveTo(), true
        );
        organizationCapacityAddOnDiscountRepository.delete(period);
        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_ORGANIZATION_CAPACITY_ADDON_DISCOUNT, null, organizationId,
                "capacityAddOnId=" + capacityAddOnId + ", periodId=" + periodId + ", discount: " + previous + " -> 기간 제거"
        );
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

    /** 조직별 할인 관리 화면 — 이 조직에 걸린 세 카탈로그 종류의 오버라이드(모든 품목·모든 기간)를 한 번에 보여준다. */
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

    // ---- 조직 횡단 목록 화면 (discount-management-screen-separation-review.md) ----
    // organizationId가 null이면 전체 조직을 대상으로 한다. 상세/수정/삭제는 기존 조직 하위
    // 엔드포인트를 그대로 재사용한다(같은 문서 6장 결정 #2) — 여기는 읽기 전용 목록만 추가한다.

    public Page<OrganizationDiscountDto.Response.BillingPlanDiscountSummary> findBillingPlanDiscountsAcrossOrganizations(
            Long organizationId, Pageable pageable
    ) {
        Page<OrganizationBillingPlanDiscount> page = organizationId != null
                ? organizationBillingPlanDiscountRepository.findAllByOrganizationId(organizationId, pageable)
                : organizationBillingPlanDiscountRepository.findAll(pageable);
        return page.map(this::toBillingPlanDiscountSummary);
    }

    public Page<OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary> findOptionalFeatureDiscountsAcrossOrganizations(
            Long organizationId, Pageable pageable
    ) {
        Page<OrganizationOptionalFeatureDiscount> page = organizationId != null
                ? organizationOptionalFeatureDiscountRepository.findAllByOrganizationId(organizationId, pageable)
                : organizationOptionalFeatureDiscountRepository.findAll(pageable);
        return page.map(this::toOptionalFeatureDiscountSummary);
    }

    public Page<OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary> findCapacityAddOnDiscountsAcrossOrganizations(
            Long organizationId, Pageable pageable
    ) {
        Page<OrganizationCapacityAddOnDiscount> page = organizationId != null
                ? organizationCapacityAddOnDiscountRepository.findAllByOrganizationId(organizationId, pageable)
                : organizationCapacityAddOnDiscountRepository.findAll(pageable);
        return page.map(this::toCapacityAddOnDiscountSummary);
    }

    // ---- CeremonyService가 스냅샷 시점(플랜 선택/구매 요청)에 쓰는 해석 로직 ----
    // 같은 패키지(feature.ceremony.service) 안에서만 쓰는 package-private 헬퍼다 — 조직/행사
    // 접근 검사와 유효 한도 계산을 CeremonyService의 package-private 헬퍼로 재사용하는 것과
    // 같은 관례(CeremonyEventService 문서 주석 참고).

    /**
     * asOfDate에 유효한 오버라이드 기간이 있으면 그 값, 없으면 호출부가 넘긴 카탈로그 자체의
     * 할인값({@code catalogDiscountType}/{@code catalogDiscountValue}) — 호출부(CeremonyService)가
     * 이미 그 시점 유효한 {@code BillingPlanPricePeriod}를 조회해뒀으므로 여기서 다시 조회하지
     * 않는다(signstage-docs business/billing-catalog-price-validity-period-review.md 결정,
     * 2026-09-09). {@code asOfDate}는 호출부가 계산해 넘긴다 — "오늘"을 어느 타임존으로 볼지는
     * 결정 #5(유보)라 이 메서드 자체는 판단하지 않는다(signstage-docs
     * business/organization-discount-override-security-and-validity-period-review.md 3.2절).
     */
    EffectiveDiscount resolveBillingPlanDiscount(
            Organization organization, Long billingPlanId, DiscountType catalogDiscountType, BigDecimal catalogDiscountValue, LocalDate asOfDate
    ) {
        return organizationBillingPlanDiscountRepository
                .findEffective(organization.getId(), billingPlanId, asOfDate)
                .map(override -> new EffectiveDiscount(override.getDiscount().getDiscountType(), override.getDiscount().getDiscountValue()))
                .orElseGet(() -> new EffectiveDiscount(catalogDiscountType, catalogDiscountValue));
    }

    EffectiveDiscount resolveOptionalFeatureDiscount(
            Organization organization, Long optionalFeatureId, DiscountType catalogDiscountType, BigDecimal catalogDiscountValue, LocalDate asOfDate
    ) {
        return organizationOptionalFeatureDiscountRepository
                .findEffective(organization.getId(), optionalFeatureId, asOfDate)
                .map(override -> new EffectiveDiscount(override.getDiscount().getDiscountType(), override.getDiscount().getDiscountValue()))
                .orElseGet(() -> new EffectiveDiscount(catalogDiscountType, catalogDiscountValue));
    }

    EffectiveDiscount resolveCapacityAddOnDiscount(
            Organization organization, Long capacityAddOnId, DiscountType catalogDiscountType, BigDecimal catalogDiscountValue, LocalDate asOfDate
    ) {
        return organizationCapacityAddOnDiscountRepository
                .findEffective(organization.getId(), capacityAddOnId, asOfDate)
                .map(override -> new EffectiveDiscount(override.getDiscount().getDiscountType(), override.getDiscount().getDiscountValue()))
                .orElseGet(() -> new EffectiveDiscount(catalogDiscountType, catalogDiscountValue));
    }

    /** {@code CeremonyService}가 스냅샷 컬럼에 그대로 옮겨 담는 해석 결과 값 객체. */
    record EffectiveDiscount(DiscountType type, BigDecimal value) {
    }

    /** 겹침 검사에 쓰는 기간 하나 — 자기 자신(수정 중인 행)은 {@code excludePeriodId}로 제외한다. */
    private record PeriodRange(Long id, LocalDate effectiveFrom, LocalDate effectiveTo) {
    }

    private void checkAllowed(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, DISCOUNT_MANAGE_PERMISSION_KEY)) {
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

    private void checkPeriodValid(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_PERIOD_INVALID);
        }
    }

    /**
     * 같은 조직×품목의 다른 기간과 겹치는지 검사한다 — signstage-docs
     * business/organization-discount-override-security-and-validity-period-review.md 3.3절.
     * MySQL은 범위 제약을 지원하지 않아 서비스 레이어에서 막는다(이 프로젝트가 이미 동시성
     * 불변식을 서비스 레이어에서 검증해온 것과 같은 방식, organization-event-discount-pricing-review.md
     * 8.4절 3차 결정).
     */
    private void checkNoOverlap(List<PeriodRange> existing, Long excludePeriodId, LocalDate newFrom, LocalDate newTo) {
        boolean overlaps = existing.stream()
                .filter(range -> excludePeriodId == null || !range.id().equals(excludePeriodId))
                .anyMatch(range -> rangesOverlap(newFrom, newTo, range.effectiveFrom(), range.effectiveTo()));
        if (overlaps) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_PERIOD_OVERLAPPING);
        }
    }

    /** null인 종료일은 무한대로 취급한다. */
    private boolean rangesOverlap(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        boolean aStartsBeforeBEnds = bTo == null || !aFrom.isAfter(bTo);
        boolean bStartsBeforeAEnds = aTo == null || !bFrom.isAfter(aTo);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    private OrganizationBillingPlanDiscount findBillingPlanDiscountPeriodOrThrow(Long organizationId, Long billingPlanId, Long periodId) {
        return organizationBillingPlanDiscountRepository.findById(periodId)
                .filter(p -> p.getOrganization().getId().equals(organizationId) && p.getBillingPlan().getId().equals(billingPlanId))
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.ORGANIZATION_DISCOUNT_PERIOD_NOT_FOUND));
    }

    private OrganizationOptionalFeatureDiscount findOptionalFeatureDiscountPeriodOrThrow(
            Long organizationId, Long optionalFeatureId, Long periodId
    ) {
        return organizationOptionalFeatureDiscountRepository.findById(periodId)
                .filter(p -> p.getOrganization().getId().equals(organizationId) && p.getOptionalFeature().getId().equals(optionalFeatureId))
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.ORGANIZATION_DISCOUNT_PERIOD_NOT_FOUND));
    }

    private OrganizationCapacityAddOnDiscount findCapacityAddOnDiscountPeriodOrThrow(
            Long organizationId, Long capacityAddOnId, Long periodId
    ) {
        return organizationCapacityAddOnDiscountRepository.findById(periodId)
                .filter(p -> p.getOrganization().getId().equals(organizationId) && p.getCapacityAddOn().getId().equals(capacityAddOnId))
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.ORGANIZATION_DISCOUNT_PERIOD_NOT_FOUND));
    }

    private String describe(DiscountType type, BigDecimal value) {
        return type == null ? "없음(카탈로그 값 사용)" : type + " " + value;
    }

    private String describeEnd(LocalDate effectiveTo) {
        return effectiveTo == null ? "무기한" : effectiveTo.toString();
    }

    /**
     * PENDING(아직 effectiveFrom 전)/ACTIVE(오늘이 기간 안)/EXPIRED(effectiveTo가 지남) —
     * 관리 화면 배지용으로 서버가 계산해 내려준다. "오늘"의 타임존은 결정 #5가 유보라 서버 기본
     * 타임존(JVM LocalDate.now())을 잠정적으로 쓴다 — 실제 청구 계산(resolveXxxDiscount)과는
     * 무관한 표시 전용 값이다.
     */
    private String computeStatus(LocalDate effectiveFrom, LocalDate effectiveTo) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(effectiveFrom)) {
            return "PENDING";
        }
        if (effectiveTo != null && today.isAfter(effectiveTo)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    /** 설정(생성/수정) 시점마다, 그리고 {@code removeXxxDiscountPeriod}에서 제거 시점마다 호출한다. */
    private void recordBillingPlanDiscountHistory(
            Organization organization, BillingPlan plan, DiscountType discountType, BigDecimal discountValue,
            LocalDate effectiveFrom, LocalDate effectiveTo, boolean removed
    ) {
        organizationBillingPlanDiscountHistoryRepository.save(
                OrganizationBillingPlanDiscountHistory.builder()
                        .organization(organization)
                        .billingPlan(plan)
                        .discountType(discountType)
                        .discountValue(discountValue)
                        .effectiveFrom(effectiveFrom)
                        .effectiveTo(effectiveTo)
                        .removed(removed)
                        .build()
        );
    }

    private void recordOptionalFeatureDiscountHistory(
            Organization organization, OptionalFeature feature, DiscountType discountType, BigDecimal discountValue,
            LocalDate effectiveFrom, LocalDate effectiveTo, boolean removed
    ) {
        organizationOptionalFeatureDiscountHistoryRepository.save(
                OrganizationOptionalFeatureDiscountHistory.builder()
                        .organization(organization)
                        .optionalFeature(feature)
                        .discountType(discountType)
                        .discountValue(discountValue)
                        .effectiveFrom(effectiveFrom)
                        .effectiveTo(effectiveTo)
                        .removed(removed)
                        .build()
        );
    }

    private void recordCapacityAddOnDiscountHistory(
            Organization organization, CapacityAddOn addOn, DiscountType discountType, BigDecimal discountValue,
            LocalDate effectiveFrom, LocalDate effectiveTo, boolean removed
    ) {
        organizationCapacityAddOnDiscountHistoryRepository.save(
                OrganizationCapacityAddOnDiscountHistory.builder()
                        .organization(organization)
                        .capacityAddOn(addOn)
                        .discountType(discountType)
                        .discountValue(discountValue)
                        .effectiveFrom(effectiveFrom)
                        .effectiveTo(effectiveTo)
                        .removed(removed)
                        .build()
        );
    }

    private OrganizationDiscountDto.Response.BillingPlanDiscountSummary toBillingPlanDiscountSummary(OrganizationBillingPlanDiscount period) {
        return new OrganizationDiscountDto.Response.BillingPlanDiscountSummary(
                period.getId(),
                period.getOrganization().getId(),
                period.getOrganization().getName(),
                period.getBillingPlan().getId(),
                period.getBillingPlan().getName(),
                period.getDiscount().getDiscountType().name(),
                period.getDiscount().getDiscountValue(),
                period.getEffectiveFrom(),
                period.getEffectiveTo(),
                computeStatus(period.getEffectiveFrom(), period.getEffectiveTo()),
                period.getCreatedAt()
        );
    }

    private OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary toOptionalFeatureDiscountSummary(
            OrganizationOptionalFeatureDiscount period
    ) {
        return new OrganizationDiscountDto.Response.OptionalFeatureDiscountSummary(
                period.getId(),
                period.getOrganization().getId(),
                period.getOrganization().getName(),
                period.getOptionalFeature().getId(),
                period.getOptionalFeature().getName(),
                period.getDiscount().getDiscountType().name(),
                period.getDiscount().getDiscountValue(),
                period.getEffectiveFrom(),
                period.getEffectiveTo(),
                computeStatus(period.getEffectiveFrom(), period.getEffectiveTo()),
                period.getCreatedAt()
        );
    }

    private OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary toCapacityAddOnDiscountSummary(
            OrganizationCapacityAddOnDiscount period
    ) {
        return new OrganizationDiscountDto.Response.CapacityAddOnDiscountSummary(
                period.getId(),
                period.getOrganization().getId(),
                period.getOrganization().getName(),
                period.getCapacityAddOn().getId(),
                period.getCapacityAddOn().getCapacityType().name(),
                period.getCapacityAddOn().getUnitAmount(),
                period.getDiscount().getDiscountType().name(),
                period.getDiscount().getDiscountValue(),
                period.getEffectiveFrom(),
                period.getEffectiveTo(),
                computeStatus(period.getEffectiveFrom(), period.getEffectiveTo()),
                period.getCreatedAt()
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
                history.getDiscount().getDiscountType().name(),
                history.getDiscount().getDiscountValue(),
                history.getEffectiveFrom(),
                history.getEffectiveTo(),
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
                history.getDiscount().getDiscountType().name(),
                history.getDiscount().getDiscountValue(),
                history.getEffectiveFrom(),
                history.getEffectiveTo(),
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
                history.getDiscount().getDiscountType().name(),
                history.getDiscount().getDiscountValue(),
                history.getEffectiveFrom(),
                history.getEffectiveTo(),
                history.isRemoved(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }
}
