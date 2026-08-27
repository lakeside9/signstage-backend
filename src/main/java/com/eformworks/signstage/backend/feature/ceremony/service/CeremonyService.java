package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.OptionalFeatureDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyAssignment;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyCapacityPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOptionalFeaturePurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyCapacityPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOptionalFeaturePurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminCeremonyPurchaseDto;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 마스터(Ceremony). signstage-docs business/ceremony-feature-migration-review.md
 * 4.1/4.6/4.7절, business/ceremony-billing-options-review.md 4.10/4.11절 참고.
 *
 * <p>조직 스코핑은 JWT 클레임이 아니라 매 요청마다 organization_members를 직접 조회해
 * 판단한다(기존 {@code MemberService}와 같은 패턴). package-private 헬퍼 일부는
 * {@link CeremonyEventService}가 같은 패키지에서 공유한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyService {

    private static final Set<String> CEREMONY_STATUS_CONTROL_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");
    private static final Set<String> PURCHASE_APPROVAL_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final CeremonyRepository ceremonyRepository;
    private final CeremonyAssignmentRepository ceremonyAssignmentRepository;
    private final CeremonyCapacityPurchaseRepository ceremonyCapacityPurchaseRepository;
    private final CeremonyOptionalFeaturePurchaseRepository ceremonyOptionalFeaturePurchaseRepository;
    private final CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    private final CeremonyPlanHistoryOptionalFeatureRepository ceremonyPlanHistoryOptionalFeatureRepository;
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanOptionalFeatureRepository billingPlanOptionalFeatureRepository;
    private final CapacityAddOnRepository capacityAddOnRepository;
    private final OptionalFeatureRepository optionalFeatureRepository;
    private final UserRepository userRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final OrganizationDiscountService organizationDiscountService;

    @Transactional
    public CeremonyDto.Response.CeremonySummary createCeremony(
            Long organizationId,
            Long currentUserId,
            CeremonyDto.Request.CreateCeremony request
    ) {
        Organization organization = findOrganizationOrThrow(organizationId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCanCreateCeremony(actingMember);

        BillingPlan plan = billingPlanRepository.findById(request.getBillingPlanId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        checkPlanActive(plan);

        Ceremony ceremony = Ceremony.builder()
                .organization(organization)
                .billingPlan(plan)
                .title(request.getTitle())
                .build();
        ceremonyRepository.save(ceremony);
        recordPlanHistory(ceremony, plan);

        // 생성자는 역할과 무관하게 자동으로 배정된다(4.7절) — 나중에 OPERATOR로 강등돼도
        // 본인이 만든 행사 접근권을 그대로 유지하는 부수 효과가 있다.
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        ceremonyAssignmentRepository.save(
                CeremonyAssignment.builder().ceremony(ceremony).user(creator).build()
        );

        return toSummary(ceremony);
    }

    /**
     * OPERATOR는 배정된 행사만 조회된다 — {@code assignedUserId}를 본인 id로 넘겨 조회 시점에
     * {@link CeremonyAssignment} 조인으로 스코핑한다({@link CeremonyRepositoryCustom#search}).
     */
    public Page<CeremonyDto.Response.CeremonySummary> findCeremonies(
            Long organizationId,
            Long currentUserId,
            String title,
            CeremonyStatus status,
            Pageable pageable
    ) {
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        Long assignedUserId = actingMember.getRole() == MemberRole.OPERATOR ? currentUserId : null;

        Page<Ceremony> ceremonies = ceremonyRepository.search(organizationId, title, status, assignedUserId, pageable);
        return ceremonies.map(this::toSummary);
    }

    /**
     * 플랫폼 관리자용 행사 목록 — {@link #findCeremonies}와 달리 조직 멤버십을 요구하지 않는다
     * (플랫폼 관리자는 별도 인가 축이라 organization_members에 없는 게 정상이다,
     * {@link #applyFinalDiscount}/{@link #updateStatusByPlatformAdmin}과 같은 이유). OPERATOR
     * 스코핑 대상이 없어 {@code assignedUserId}는 항상 null이다 — 관리자는 전부 본다. 조회 전용이라
     * {@link #applyFinalDiscount}처럼 등급 검사를 하지 않는다(카탈로그 조회 API들과 같은 관례,
     * PLATFORM_SUPPORT 이상이면 누구나 — 이미 SecurityConfig가 /api/platform-admin/**를 게이트).
     */
    public Page<CeremonyDto.Response.CeremonySummary> findCeremoniesByPlatformAdmin(
            Long organizationId,
            String title,
            CeremonyStatus status,
            Pageable pageable
    ) {
        findOrganizationOrThrow(organizationId);
        Page<Ceremony> ceremonies = ceremonyRepository.search(organizationId, title, status, null, pageable);
        return ceremonies.map(this::toSummary);
    }

    public CeremonyDto.Response.CeremonySummary retrieveCeremony(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);
        return toSummary(ceremony);
    }

    /** 행사 수정 화면에서 이름/설명을 바꾼다. 플랜은 생성 시점에 고정이라 여기서 바꾸지 않는다. */
    @Transactional
    public CeremonyDto.Response.CeremonySummary updateCeremony(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.UpdateCeremony request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyEditable(ceremony);

        ceremony.updateInfo(
                request.getTitle(),
                request.getDescription(),
                request.getOrganizingInstitution(),
                request.getOrganizingDepartment(),
                request.getContactName(),
                request.getContactTitle(),
                request.getContactPhone(),
                request.getContactEmail()
        );
        return toSummary(ceremony);
    }

    /**
     * DRAFT 상태에서만 플랜을 바꿀 수 있다 — 확정 후(IN_PROGRESS/COMPLETED) 시도하면 거부한다.
     * 호출할 때마다 {@link CeremonyPlanHistory}에 이력을 한 행 남긴다(3.2/3.4절).
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary changePlan(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.ChangePlan request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyPlanChangeable(ceremony);

        BillingPlan newPlan = billingPlanRepository.findById(request.getBillingPlanId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        checkPlanActive(newPlan);

        ceremony.changePlan(newPlan);
        recordPlanHistory(ceremony, newPlan);

        return toSummary(ceremony);
    }

    /**
     * "플랜 확정" — DRAFT → IN_PROGRESS로 단방향 전이한다. 이후 플랜은 고정되고, 서명자/문서/
     * 하위 행사 등록이 열린다(3.1절). 확정을 취소하는 API는 두지 않는다(4.4절).
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary confirmPlan(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyPlanChangeable(ceremony);

        ceremony.confirmPlan();

        return toSummary(ceremony);
    }

    /** 최신순 — 가장 앞이 확정(또는 가장 최근 변경) 시점의 스냅샷이다. */
    public List<CeremonyDto.Response.PlanHistorySummary> findPlanHistory(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyPlanHistoryRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(this::toPlanHistorySummary)
                .toList();
    }

    @Transactional
    public CeremonyDto.Response.CapacityPurchaseSummary purchaseCapacity(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.PurchaseCapacity request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyEditable(ceremony);

        CapacityAddOn addOn = capacityAddOnRepository.findById(request.getCapacityAddOnId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_NOT_FOUND));
        if (!addOn.isActive()) {
            throw new ApplicationException(CeremonyErrorCode.CAPACITY_ADDON_INACTIVE);
        }

        // 조직×용량추가구매 할인 오버라이드가 있으면 카탈로그 값 대신 이 값을 스냅샷한다 —
        // recordPlanHistory와 같은 원칙(4.1절, 2026-08-21 재검토).
        OrganizationDiscountService.EffectiveDiscount discount =
                organizationDiscountService.resolveCapacityAddOnDiscount(ceremony.getOrganization(), addOn);

        CeremonyCapacityPurchase purchase = CeremonyCapacityPurchase.builder()
                .ceremony(ceremony)
                .capacityAddOn(addOn)
                .quantity(request.getQuantity())
                .purchasedUnitAmount(addOn.getUnitAmount())
                .purchasedSecondaryUnitAmount(addOn.getSecondaryUnitAmount())
                .purchasedSalePrice(addOn.getSalePrice())
                .purchasedDiscountType(discount.type())
                .purchasedDiscountValue(discount.value())
                .build();
        ceremonyCapacityPurchaseRepository.save(purchase);

        return toCapacitySummary(purchase);
    }

    /** 요청자 본인 이력 조회 — 대기중/승인됨/반려됨 전부 보여준다. */
    public List<CeremonyDto.Response.CapacityPurchaseSummary> findCapacityPurchases(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyCapacityPurchaseRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(this::toCapacitySummary)
                .toList();
    }

    @Transactional
    public CeremonyDto.Response.OptionalFeaturePurchaseSummary purchaseOptionalFeature(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.PurchaseOptionalFeature request
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyEditable(ceremony);

        OptionalFeature feature = optionalFeatureRepository.findById(request.getOptionalFeatureId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));
        if (!feature.isActive()) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_INACTIVE);
        }

        boolean alreadyRequested = ceremonyOptionalFeaturePurchaseRepository.existsByCeremonyIdAndOptionalFeatureIdAndStatusIn(
                ceremonyId, feature.getId(), List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)
        );
        if (alreadyRequested) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_ALREADY_PURCHASED);
        }

        // 조직×선택옵션 할인 오버라이드가 있으면 카탈로그 값 대신 이 값을 스냅샷한다 —
        // recordPlanHistory와 같은 원칙(4.1절, 2026-08-21 재검토).
        OrganizationDiscountService.EffectiveDiscount discount =
                organizationDiscountService.resolveOptionalFeatureDiscount(ceremony.getOrganization(), feature);

        CeremonyOptionalFeaturePurchase purchase = CeremonyOptionalFeaturePurchase.builder()
                .ceremony(ceremony)
                .optionalFeature(feature)
                .purchasedName(feature.getName())
                .purchasedSalePrice(feature.getSalePrice())
                .purchasedDiscountType(discount.type())
                .purchasedDiscountValue(discount.value())
                .build();
        ceremonyOptionalFeaturePurchaseRepository.save(purchase);

        return toOptionalFeatureSummary(purchase);
    }

    /** 요청자 본인 이력 조회 — 대기중/승인됨/반려됨 전부 보여준다. */
    public List<CeremonyDto.Response.OptionalFeaturePurchaseSummary> findOptionalFeaturePurchases(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyOptionalFeaturePurchaseRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(this::toOptionalFeatureSummary)
                .toList();
    }

    /**
     * 이 Ceremony가 하위 행사에 실제로 적용할 수 있는 선택옵션(플랜 포함분 + 승인된 추가구매)
     * 카탈로그만 필터링해 돌려준다 — {@link #retrievePurchasedOptionalFeatureIds}와 같은 계산을
     * 쓴다. 하위 행사 등록/수정/상세 세 화면이 전부 이 목록으로 체크박스를 채운다(구매 안 한
     * 옵션을 보여줬다가 저장 시점에야 실패를 아는 예전 방식의 한계를 없앤다).
     *
     * <p>개별 추가구매(승인됨)한 옵션은 이름/가격을 구매 시점 스냅샷({@code purchasedName}/
     * {@code purchasedSalePrice} 등)으로 보여준다 — 카탈로그 관리자가 나중에 이름을 바꿔도
     * 이미 구매한 옵션의 표시는 안 바뀐다(signstage-docs
     * business/ceremony-billing-options-review.md 9장). 플랜에 기본 포함된 옵션(추가구매
     * 기록이 없음)은 스냅샷 대상이 아니라 카탈로그 값을 그대로 보여준다 — 애초에 개별로
     * "산" 적이 없어 가격을 보호할 대상 자체가 없다.
     */
    public List<OptionalFeatureDto.Response.OptionalFeatureSummary> retrieveAvailableOptionalFeatures(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        List<Long> availableIds = retrievePurchasedOptionalFeatureIds(ceremony);
        if (availableIds.isEmpty()) {
            return List.of();
        }

        Map<Long, CeremonyOptionalFeaturePurchase> approvedPurchaseByFeatureId = ceremonyOptionalFeaturePurchaseRepository
                .findAllByCeremonyIdAndStatus(ceremony.getId(), PurchaseStatus.APPROVED).stream()
                .collect(Collectors.toMap(purchase -> purchase.getOptionalFeature().getId(), purchase -> purchase));

        return optionalFeatureRepository.findAllById(availableIds).stream()
                .map(feature -> {
                    CeremonyOptionalFeaturePurchase purchase = approvedPurchaseByFeatureId.get(feature.getId());
                    return new OptionalFeatureDto.Response.OptionalFeatureSummary(
                            feature.getId(),
                            feature.getCode().name(),
                            purchase != null ? purchase.getPurchasedName() : feature.getName(),
                            feature.getSupplyPrice(),
                            purchase != null ? purchase.getPurchasedSalePrice() : feature.getSalePrice(),
                            purchase != null ? purchase.getPurchasedDiscountType().name() : feature.getDiscountType().name(),
                            purchase != null ? purchase.getPurchasedDiscountValue() : feature.getDiscountValue(),
                            feature.isActive(),
                            feature.isProjectorEffect(),
                            feature.getExclusivityGroup(),
                            ceremonyOptionalFeaturePurchaseRepository.countByOptionalFeatureIdAndStatus(
                                    feature.getId(), PurchaseStatus.APPROVED
                            ),
                            feature.getCreatedAt()
                    );
                })
                .toList();
    }

    /**
     * 플랫폼 관리자가 Ceremony 상태를 IN_PROGRESS/COMPLETED 사이에서 양방향으로 강제 변경한다
     * (실수로 완료됐거나 예외 상황 처리용). DRAFT는 대상이 아니다 — 플랜 확정(DRAFT →
     * IN_PROGRESS)은 {@link #confirmPlan}의 단방향 전이로만 이뤄진다(signstage-docs
     * business/ceremony-plan-confirmation-review.md 4.4절). {@code feature.platformadmin.service}에
     * 별도 래퍼를 두지 않고 여기 직접 붙인다 — 과금 카탈로그 작업에서 확립한 관례
     * (PlatformAdminBillingCatalogController → BillingPlanService 등)와 같다. 아래
     * {@code findCeremonyInOrganizationOrThrow}를 그대로 재사용한다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary updateStatusByPlatformAdmin(
            Long organizationId,
            Long ceremonyId,
            Long adminUserId,
            String actingPlatformRole,
            CeremonyDto.Request.UpdateStatus request
    ) {
        if (!CEREMONY_STATUS_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        CeremonyStatus previousStatus = ceremony.getStatus();
        CeremonyStatus newStatus = parseCeremonyStatus(request.getStatus());
        if (newStatus == CeremonyStatus.DRAFT) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        ceremony.changeStatus(newStatus);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CEREMONY_STATUS, null, organizationId,
                "ceremonyId=" + ceremonyId + ", status: " + previousStatus + " -> " + newStatus
        );

        return toSummary(ceremony);
    }

    private CeremonyStatus parseCeremonyStatus(String status) {
        try {
            return CeremonyStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 행사 건별 재량 할인 설정 — 플랫폼 관리자(PLATFORM_OPS 이상) 전용이고, 플랜이 확정된
     * (IN_PROGRESS) 행사에만 적용할 수 있다. DRAFT는 "아직 플랜도 안 정해졌는데 할인부터
     * 매길 수 없다"는 이유로, COMPLETED는 "끝난 행사는 더 이상 안 바뀐다"는 기존 원칙으로
     * 막는다 — signstage-docs business/organization-event-discount-pricing-review.md
     * 4.2/4.4/6.2절 참고. {@code feature.platformadmin.service}에 별도 래퍼를 두지 않는 이유는
     * 위 {@link #updateStatusByPlatformAdmin}과 같다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary applyFinalDiscount(
            Long organizationId,
            Long ceremonyId,
            Long adminUserId,
            String actingPlatformRole,
            CeremonyDto.Request.ApplyFinalDiscount request
    ) {
        if (!CEREMONY_STATUS_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }

        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        checkCeremonyInProgress(ceremony);

        DiscountType previousType = ceremony.getFinalDiscountType();
        BigDecimal previousValue = ceremony.getFinalDiscountValue();
        DiscountType newType = parseDiscountType(request.getDiscountType());

        ceremony.applyFinalDiscount(newType, request.getDiscountValue());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CEREMONY_FINAL_DISCOUNT, null, organizationId,
                "ceremonyId=" + ceremonyId + ", finalDiscount: " + previousType + " " + previousValue
                        + " -> " + newType + " " + request.getDiscountValue()
        );

        return toSummary(ceremony);
    }

    /** DRAFT(플랜 미확정)·COMPLETED(완료) 둘 다 막고 IN_PROGRESS만 허용한다. */
    private void checkCeremonyInProgress(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        }
        if (ceremony.getStatus() == CeremonyStatus.COMPLETED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_ALREADY_COMPLETED);
        }
    }

    private DiscountType parseDiscountType(String discountType) {
        try {
            return DiscountType.valueOf(discountType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    // ---- 플랫폼 관리자 — 용량/선택옵션 추가구매 승인 대기열 ----
    // feature.platformadmin.service에 별도 래퍼를 두지 않고 여기 직접 붙인다(위 updateStatusByPlatformAdmin과 같은 이유).

    /** status를 생략하면(null) 전체 상태를 최신순으로 돌려준다(조직 생성 요청 목록과 같은 규약). */
    public Page<PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary> findCapacityPurchaseRequests(
            PurchaseStatus status,
            Pageable pageable
    ) {
        Page<CeremonyCapacityPurchase> purchases = status != null
                ? ceremonyCapacityPurchaseRepository.findAllByStatus(status, pageable)
                : ceremonyCapacityPurchaseRepository.findAll(pageable);
        Map<Long, String> loginIdsByUserId = resolveUserLoginIds(
                purchases.getContent().stream().flatMap(purchase -> Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy()))
        );
        return purchases.map(purchase -> toCapacityRequestSummary(purchase, loginIdsByUserId));
    }

    @Transactional
    public PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary approveCapacityPurchase(
            Long purchaseId,
            Long adminUserId,
            String actingPlatformRole
    ) {
        if (!PURCHASE_APPROVAL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        CeremonyCapacityPurchase purchase = findPendingCapacityPurchaseOrThrow(purchaseId);
        purchase.approve(adminUserId);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.APPROVE_CAPACITY_PURCHASE, null,
                purchase.getCeremony().getOrganization().getId(),
                "purchaseId=" + purchaseId + ", ceremonyId=" + purchase.getCeremony().getId()
        );
        return toCapacityRequestSummary(purchase, resolveUserLoginIds(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy())));
    }

    @Transactional
    public PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary rejectCapacityPurchase(
            Long purchaseId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminCeremonyPurchaseDto.Request.Reject request
    ) {
        if (!PURCHASE_APPROVAL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        CeremonyCapacityPurchase purchase = findPendingCapacityPurchaseOrThrow(purchaseId);
        purchase.reject(adminUserId, request.getRejectionReason());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.REJECT_CAPACITY_PURCHASE, null,
                purchase.getCeremony().getOrganization().getId(),
                "purchaseId=" + purchaseId + ", reason=" + request.getRejectionReason()
        );
        return toCapacityRequestSummary(purchase, resolveUserLoginIds(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy())));
    }

    public Page<PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary> findOptionalFeaturePurchaseRequests(
            PurchaseStatus status,
            Pageable pageable
    ) {
        Page<CeremonyOptionalFeaturePurchase> purchases = status != null
                ? ceremonyOptionalFeaturePurchaseRepository.findAllByStatus(status, pageable)
                : ceremonyOptionalFeaturePurchaseRepository.findAll(pageable);
        Map<Long, String> loginIdsByUserId = resolveUserLoginIds(
                purchases.getContent().stream().flatMap(purchase -> Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy()))
        );
        return purchases.map(purchase -> toOptionalFeatureRequestSummary(purchase, loginIdsByUserId));
    }

    @Transactional
    public PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary approveOptionalFeaturePurchase(
            Long purchaseId,
            Long adminUserId,
            String actingPlatformRole
    ) {
        if (!PURCHASE_APPROVAL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        CeremonyOptionalFeaturePurchase purchase = findPendingOptionalFeaturePurchaseOrThrow(purchaseId);
        purchase.approve(adminUserId);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.APPROVE_OPTIONAL_FEATURE_PURCHASE, null,
                purchase.getCeremony().getOrganization().getId(),
                "purchaseId=" + purchaseId + ", ceremonyId=" + purchase.getCeremony().getId()
        );
        return toOptionalFeatureRequestSummary(purchase, resolveUserLoginIds(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy())));
    }

    @Transactional
    public PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary rejectOptionalFeaturePurchase(
            Long purchaseId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminCeremonyPurchaseDto.Request.Reject request
    ) {
        if (!PURCHASE_APPROVAL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        CeremonyOptionalFeaturePurchase purchase = findPendingOptionalFeaturePurchaseOrThrow(purchaseId);
        purchase.reject(adminUserId, request.getRejectionReason());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.REJECT_OPTIONAL_FEATURE_PURCHASE, null,
                purchase.getCeremony().getOrganization().getId(),
                "purchaseId=" + purchaseId + ", reason=" + request.getRejectionReason()
        );
        return toOptionalFeatureRequestSummary(purchase, resolveUserLoginIds(Stream.of(purchase.getCreatedBy(), purchase.getReviewedBy())));
    }

    private CeremonyCapacityPurchase findPendingCapacityPurchaseOrThrow(Long purchaseId) {
        CeremonyCapacityPurchase purchase = ceremonyCapacityPurchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CAPACITY_PURCHASE_NOT_FOUND));
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new ApplicationException(CeremonyErrorCode.CAPACITY_PURCHASE_NOT_PENDING);
        }
        return purchase;
    }

    private CeremonyOptionalFeaturePurchase findPendingOptionalFeaturePurchaseOrThrow(Long purchaseId) {
        CeremonyOptionalFeaturePurchase purchase = ceremonyOptionalFeaturePurchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_PURCHASE_NOT_FOUND));
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_PURCHASE_NOT_PENDING);
        }
        return purchase;
    }

    private Map<Long, String> resolveUserLoginIds(Stream<Long> userIds) {
        List<Long> ids = userIds.filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getLoginId));
    }

    // ---- CeremonyEventService와 공유하는 package-private 헬퍼 ----

    Ceremony findCeremonyInOrganizationOrThrow(Long organizationId, Long ceremonyId) {
        Ceremony ceremony = ceremonyRepository.findById(ceremonyId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CEREMONY_NOT_FOUND));
        if (!ceremony.getOrganization().getId().equals(organizationId)) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_NOT_FOUND);
        }
        return ceremony;
    }

    Member findActiveMemberOrThrow(Long organizationId, Long userId) {
        return memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
    }

    /** OWNER/ADMIN/VIEWER는 조직의 모든 행사를 조회할 수 있고, OPERATOR는 배정된 행사만 조회할 수 있다. */
    void checkCeremonyReadAccess(Ceremony ceremony, Member actingMember, Long currentUserId) {
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            checkAssigned(ceremony, currentUserId);
        }
    }

    /**
     * 행사 생성/수정(용량·옵션 구매, 하위 행사 생성 등) 권한. OWNER/ADMIN은 항상 가능하고,
     * OPERATOR는 배정된 행사만, VIEWER는 불가하다(user-organization-design.md 4.2절
     * "행사(Ceremony) 생성/수정/삭제").
     */
    void checkCeremonyManageAccess(Ceremony ceremony, Member actingMember, Long currentUserId) {
        if (actingMember.getRole() == MemberRole.VIEWER) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            checkAssigned(ceremony, currentUserId);
        }
    }

    /**
     * 완료(COMPLETED)된 Ceremony 아래에서는 하위 데이터를 더 이상 수정할 수 없다 — 조회만 가능하다.
     * 결과물 생성처럼 완료를 유발하는 호출 자체는 이 체크가 통과한 뒤(아직 IN_PROGRESS일 때)
     * 실행되고, 완료 전이는 그 성공 이후에 일어나므로 스스로를 막지 않는다.
     */
    void checkCeremonyEditable(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.COMPLETED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_ALREADY_COMPLETED);
        }
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
     * 서명자/문서/하위 행사 등록은 플랜이 확정된(DRAFT를 벗어난) Ceremony에서만 허용한다.
     * {@link SignerService}/{@link TemplateService}/{@link CeremonyEventService}가
     * {@link #checkCeremonyEditable}과 함께 재사용한다 — signstage-docs
     * business/ceremony-plan-confirmation-review.md 3.3절.
     */
    void checkCeremonyPlanConfirmed(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        }
    }

    /** 사용 중지(active=false)된 플랜은 신규 선택/변경 대상에서 제외한다. */
    private void checkPlanActive(BillingPlan plan) {
        if (!plan.isActive()) {
            throw new ApplicationException(CeremonyErrorCode.BILLING_PLAN_INACTIVE);
        }
    }

    /**
     * Ceremony 생성 시(최초 플랜 선택)와 {@link #changePlan}에서 매 변경마다 호출한다(3.4절).
     * 그 순간 플랜에 포함된 선택옵션 구성도 {@link CeremonyPlanHistoryOptionalFeature}로 함께
     * 스냅샷한다 — 카탈로그 관리자가 나중에 플랜의 옵션 구성을 바꿔도 이 Ceremony는 영향받지
     * 않아야 한다(signstage-docs business/ceremony-billing-options-review.md 9장 후속 결정).
     */
    private void recordPlanHistory(Ceremony ceremony, BillingPlan plan) {
        // 조직×플랜 할인 오버라이드가 있으면 카탈로그 값 대신 이 값을 스냅샷한다
        // (signstage-docs business/organization-event-discount-pricing-review.md 4.1절,
        // 2026-08-21 재검토) — 그 시점의 값을 CeremonyPlanHistory에 고정해 두므로, 오버라이드를
        // 나중에 바꿔도 이미 만들어진 이 Ceremony에는 영향을 주지 않는다.
        OrganizationDiscountService.EffectiveDiscount discount =
                organizationDiscountService.resolveBillingPlanDiscount(ceremony.getOrganization(), plan);
        CeremonyPlanHistory history = ceremonyPlanHistoryRepository.save(
                CeremonyPlanHistory.builder()
                        .ceremony(ceremony)
                        .billingPlan(plan)
                        .discountType(discount.type())
                        .discountValue(discount.value())
                        .build()
        );
        billingPlanOptionalFeatureRepository.findAllByBillingPlanId(plan.getId()).forEach(mapping ->
                ceremonyPlanHistoryOptionalFeatureRepository.save(
                        CeremonyPlanHistoryOptionalFeature.builder()
                                .ceremonyPlanHistory(history)
                                .optionalFeature(mapping.getOptionalFeature())
                                .build()
                )
        );
    }

    /**
     * 서명자/문서양식/테스트·본행사 등록 화면이 "등록할 수 있는 개수"를 보여주는 데 쓴다 —
     * {@link #calculateEffectiveCapacity}(플랜 기본값 + 승인된 추가구매)를 다섯 가지 용량 유형
     * 전부에 대해 계산해 돌려준다(2026-08-27 REHEARSAL_EVENTS 추가). 플랜이 없는 행사는
     * 무제한이라 Integer.MAX_VALUE를 그대로 돌려준다(프런트가 "무제한"으로 표시).
     */
    public CeremonyDto.Response.CapacityStatus retrieveCapacityStatus(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return new CeremonyDto.Response.CapacityStatus(
                calculateEffectiveCapacity(ceremony, CapacityType.SIGNERS),
                calculateEffectiveCapacity(ceremony, CapacityType.TEMPLATES),
                calculateEffectiveCapacity(ceremony, CapacityType.TEST_EVENTS),
                calculateEffectiveCapacity(ceremony, CapacityType.REHEARSAL_EVENTS),
                calculateEffectiveCapacity(ceremony, CapacityType.MAIN_EVENTS)
        );
    }

    /**
     * 이 Ceremony의 예상 청구 금액 — 품목 할인 → subtotal → 행사 건별 할인의 2단 순차 차감
     * (signstage-docs business/organization-event-discount-pricing-review.md 4.3절). 실제
     * 결제/청구서 발행은 여전히 범위 밖이다(같은 문서 5장) — "지금 계산하면 얼마인지"를
     * 보여주는 견적용 계산이다.
     *
     * <p>플랜 항목은 라이브 {@code BillingPlan}이 아니라 {@link CeremonyPlanHistory} 최신
     * 스냅샷을 쓴다 — 9장과 같은 원칙으로, 이력이 없는 행사(이 기능 배포 전 기존 행사)만
     * 라이브로 폴백한다. 용량/선택옵션 추가구매는 승인(APPROVED)된 건만 반영하고, 그 구매
     * 시점 스냅샷(가격·단가)을 그대로 쓴다.
     */
    public CeremonyDto.Response.EstimatedTotal calculateEstimatedTotal(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = findActiveMemberOrThrow(organizationId, currentUserId);
        checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        BigDecimal planApplied = BigDecimal.ZERO;
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan != null) {
            Optional<CeremonyPlanHistory> snapshot =
                    ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(ceremony.getId());
            planApplied = snapshot
                    .map(history -> applyDiscount(history.getPlanSalePrice(), history.getPlanDiscountType(), history.getPlanDiscountValue()))
                    .orElseGet(() -> applyDiscount(plan.getSalePrice(), plan.getDiscountType(), plan.getDiscountValue()));
        }

        BigDecimal capacityTotal = ceremonyCapacityPurchaseRepository
                .findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .filter(purchase -> purchase.getStatus() == PurchaseStatus.APPROVED)
                .map(purchase -> applyDiscount(
                        purchase.getPurchasedSalePrice().multiply(BigDecimal.valueOf(purchase.getQuantity())),
                        purchase.getPurchasedDiscountType(),
                        purchase.getPurchasedDiscountValue()
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal featureTotal = ceremonyOptionalFeaturePurchaseRepository
                .findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .filter(purchase -> purchase.getStatus() == PurchaseStatus.APPROVED)
                .map(purchase -> applyDiscount(
                        purchase.getPurchasedSalePrice(), purchase.getPurchasedDiscountType(), purchase.getPurchasedDiscountValue()
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal subtotal = planApplied.add(capacityTotal).add(featureTotal);
        BigDecimal finalTotal = applyDiscount(subtotal, ceremony.getFinalDiscountType(), ceremony.getFinalDiscountValue());

        return new CeremonyDto.Response.EstimatedTotal(
                planApplied,
                capacityTotal,
                featureTotal,
                subtotal,
                ceremony.getFinalDiscountType().name(),
                ceremony.getFinalDiscountValue(),
                finalTotal
        );
    }

    /** 정률(PERCENT)/정액(FIXED_AMOUNT) 할인을 적용한다 — 결과가 0 밑으로 내려가지 않게 막는다. */
    private BigDecimal applyDiscount(BigDecimal amount, DiscountType discountType, BigDecimal discountValue) {
        BigDecimal discount = discountType == DiscountType.PERCENT
                ? amount.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : discountValue;
        return amount.subtract(discount).max(BigDecimal.ZERO);
    }

    /**
     * 필수옵션(용량) 유효 한도 = 플랜 기본값(스냅샷) + Σ(구매수량 × 구매 시점 단가 스냅샷). 플랜이
     * 없는 행사(4.8 예외 — 이 기능 배포 전 기존 행사)는 한도 강제 자체를 적용하지 않는다(사실상
     * 무제한).
     *
     * <p>플랜 기본값은 라이브 {@code BillingPlan}이 아니라 {@link CeremonyPlanHistory}의 최신
     * 스냅샷을 쓴다 — 카탈로그 관리자가 나중에 플랜 값을 고쳐도 이미 확정/진행 중인 행사는
     * 영향받지 않아야 한다(signstage-docs business/ceremony-billing-options-review.md 9장).
     * 이 기능(플랜 확정) 배포 전에 만들어져 이력이 없는 행사만 예외로 라이브 값에 fallback한다.
     * 추가구매 단가도 같은 이유로 {@code capacityAddOn.getUnitAmount()} 라이브 조회 대신
     * 구매 시점 스냅샷({@code purchasedUnitAmount})을 쓴다.
     */
    int calculateEffectiveCapacity(Ceremony ceremony, CapacityType capacityType) {
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan == null) {
            return Integer.MAX_VALUE;
        }

        int baseValue = ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(ceremony.getId())
                .map(snapshot -> switch (capacityType) {
                    case SIGNERS -> snapshot.getPlanMaxSigners();
                    case TEMPLATES -> snapshot.getPlanMaxTemplates();
                    case TEST_EVENTS -> snapshot.getPlanMaxTestEvents();
                    case REHEARSAL_EVENTS -> snapshot.getPlanMaxRehearsalEvents();
                    case MAIN_EVENTS -> snapshot.getPlanMaxMainEvents();
                    // 태블릿은 플랜 기본 포함 개념이 없다 — 항상 0에서 시작해 추가구매로만 늘어난다.
                    case TABLETS -> 0;
                })
                // 이력이 없는 경우(플랜 확정 기능 배포 전 기존 행사)만 라이브 값으로 대체한다.
                .orElseGet(() -> switch (capacityType) {
                    case SIGNERS -> plan.getMaxSigners();
                    case TEMPLATES -> plan.getMaxTemplates();
                    case TEST_EVENTS -> plan.getMaxTestEvents();
                    case REHEARSAL_EVENTS -> plan.getMaxRehearsalEvents();
                    case MAIN_EVENTS -> plan.getMaxMainEvents();
                    case TABLETS -> 0;
                });

        // 승인(APPROVED)된 요청만 한도에 반영한다 — 대기중/반려된 요청은 아직/영영 쓸 수 없다.
        // 이 유형을 주(capacityType)로 파는 상품과, 묶음 상품의 보조(secondaryCapacityType)로
        // 파는 상품(예: "서명자+태블릿") 둘 다 반영한다 — signstage-docs
        // business/ceremony-billing-options-review.md 4.7절 후속(2026-08-21).
        int purchasedAsPrimary = ceremonyCapacityPurchaseRepository
                .findAllByCeremonyIdAndCapacityAddOn_CapacityTypeAndStatus(ceremony.getId(), capacityType, PurchaseStatus.APPROVED)
                .stream()
                .mapToInt(purchase -> purchase.getQuantity() * purchase.getPurchasedUnitAmount())
                .sum();
        int purchasedAsSecondary = ceremonyCapacityPurchaseRepository
                .findAllByCeremonyIdAndCapacityAddOn_SecondaryCapacityTypeAndStatus(ceremony.getId(), capacityType, PurchaseStatus.APPROVED)
                .stream()
                .mapToInt(purchase -> purchase.getQuantity() * purchase.getPurchasedSecondaryUnitAmount())
                .sum();

        return baseValue + purchasedAsPrimary + purchasedAsSecondary;
    }

    /**
     * Ceremony가 "구매한"(플랜 기본 포함 또는 추가구매) 선택옵션 id 집합(4.11절). 추가구매 쪽은
     * 승인(APPROVED)된 요청만 포함한다 — 대기중/반려된 요청은 아직/영영 적용할 수 없다.
     *
     * <p>"플랜 기본 포함" 쪽은 라이브 {@code BillingPlanOptionalFeature} 대신 이 Ceremony의
     * 최신 {@link CeremonyPlanHistory} 스냅샷(연결된 {@link CeremonyPlanHistoryOptionalFeature})을
     * 우선 쓴다 — 카탈로그 관리자가 나중에 플랜의 옵션 구성을 바꿔도 영향받지 않아야 한다
     * (signstage-docs business/ceremony-billing-options-review.md 9장 후속 결정). 이력이 없는
     * 경우(이 스냅샷 기능 배포 전 기존 행사)만 라이브 값으로 대체한다.
     */
    List<Long> retrievePurchasedOptionalFeatureIds(Ceremony ceremony) {
        List<Long> purchased = ceremonyOptionalFeaturePurchaseRepository
                .findAllByCeremonyIdAndStatus(ceremony.getId(), PurchaseStatus.APPROVED).stream()
                .map(purchase -> purchase.getOptionalFeature().getId())
                .toList();
        if (ceremony.getBillingPlan() == null) {
            return purchased;
        }

        Optional<CeremonyPlanHistory> snapshot =
                ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(ceremony.getId());
        List<Long> includedInPlan = snapshot
                .map(history -> ceremonyPlanHistoryOptionalFeatureRepository.findAllByCeremonyPlanHistoryId(history.getId())
                        .stream()
                        .map(mapping -> mapping.getOptionalFeature().getId())
                        .toList())
                .orElseGet(() -> billingPlanOptionalFeatureRepository
                        .findAllByBillingPlanId(ceremony.getBillingPlan().getId()).stream()
                        .map(mapping -> mapping.getOptionalFeature().getId())
                        .toList());

        return Stream.concat(purchased.stream(), includedInPlan.stream()).distinct().toList();
    }

    private void checkAssigned(Ceremony ceremony, Long currentUserId) {
        if (!ceremonyAssignmentRepository.existsByCeremonyIdAndUserId(ceremony.getId(), currentUserId)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void checkCanCreateCeremony(Member actingMember) {
        if (actingMember.getRole() == MemberRole.VIEWER) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private CeremonyDto.Response.CeremonySummary toSummary(Ceremony ceremony) {
        return new CeremonyDto.Response.CeremonySummary(
                ceremony.getId(),
                ceremony.getOrganization().getId(),
                ceremony.getBillingPlan() != null ? ceremony.getBillingPlan().getId() : null,
                ceremony.getTitle(),
                ceremony.getDescription(),
                ceremony.getStatus().name(),
                ceremony.getOrganizingInstitution(),
                ceremony.getOrganizingDepartment(),
                ceremony.getContactName(),
                ceremony.getContactTitle(),
                ceremony.getContactPhone(),
                ceremony.getContactEmail(),
                ceremony.getFinalDiscountType().name(),
                ceremony.getFinalDiscountValue(),
                ceremony.getCreatedBy(),
                ceremony.getCreatedAt()
        );
    }

    private CeremonyDto.Response.PlanHistorySummary toPlanHistorySummary(CeremonyPlanHistory history) {
        return new CeremonyDto.Response.PlanHistorySummary(
                history.getId(),
                history.getBillingPlan().getId(),
                history.getPlanName(),
                history.getPlanSupplyPrice(),
                history.getPlanSalePrice(),
                history.getPlanDiscountType().name(),
                history.getPlanDiscountValue(),
                history.getPlanMaxSigners(),
                history.getPlanMaxTemplates(),
                history.getPlanMaxTestEvents(),
                history.getPlanMaxRehearsalEvents(),
                history.getPlanMaxMainEvents(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private CeremonyDto.Response.CapacityPurchaseSummary toCapacitySummary(CeremonyCapacityPurchase purchase) {
        return new CeremonyDto.Response.CapacityPurchaseSummary(
                purchase.getId(),
                purchase.getCeremony().getId(),
                purchase.getCapacityAddOn().getId(),
                purchase.getQuantity(),
                purchase.getPurchasedUnitAmount(),
                purchase.getPurchasedSecondaryUnitAmount(),
                purchase.getPurchasedSalePrice(),
                purchase.getPurchasedDiscountType().name(),
                purchase.getPurchasedDiscountValue(),
                purchase.getStatus().name(),
                purchase.getRejectionReason(),
                purchase.getReviewedAt(),
                purchase.getCreatedAt()
        );
    }

    private CeremonyDto.Response.OptionalFeaturePurchaseSummary toOptionalFeatureSummary(
            CeremonyOptionalFeaturePurchase purchase
    ) {
        return new CeremonyDto.Response.OptionalFeaturePurchaseSummary(
                purchase.getId(),
                purchase.getCeremony().getId(),
                purchase.getOptionalFeature().getId(),
                purchase.getPurchasedName(),
                purchase.getPurchasedSalePrice(),
                purchase.getPurchasedDiscountType().name(),
                purchase.getPurchasedDiscountValue(),
                purchase.getStatus().name(),
                purchase.getRejectionReason(),
                purchase.getReviewedAt(),
                purchase.getCreatedAt()
        );
    }

    private PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary toCapacityRequestSummary(
            CeremonyCapacityPurchase purchase,
            Map<Long, String> loginIdsByUserId
    ) {
        return new PlatformAdminCeremonyPurchaseDto.Response.CapacityPurchaseRequestSummary(
                purchase.getId(),
                purchase.getCreatedBy(),
                loginIdsByUserId.get(purchase.getCreatedBy()),
                purchase.getCeremony().getOrganization().getId(),
                purchase.getCeremony().getId(),
                purchase.getCeremony().getTitle(),
                purchase.getCapacityAddOn().getId(),
                purchase.getQuantity(),
                purchase.getPurchasedSalePrice(),
                purchase.getStatus().name(),
                purchase.getRejectionReason(),
                purchase.getReviewedBy() != null ? loginIdsByUserId.get(purchase.getReviewedBy()) : null,
                purchase.getReviewedAt(),
                purchase.getCreatedAt()
        );
    }

    private PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary toOptionalFeatureRequestSummary(
            CeremonyOptionalFeaturePurchase purchase,
            Map<Long, String> loginIdsByUserId
    ) {
        return new PlatformAdminCeremonyPurchaseDto.Response.OptionalFeaturePurchaseRequestSummary(
                purchase.getId(),
                purchase.getCreatedBy(),
                loginIdsByUserId.get(purchase.getCreatedBy()),
                purchase.getCeremony().getOrganization().getId(),
                purchase.getCeremony().getId(),
                purchase.getCeremony().getTitle(),
                purchase.getOptionalFeature().getId(),
                purchase.getPurchasedSalePrice(),
                purchase.getStatus().name(),
                purchase.getRejectionReason(),
                purchase.getReviewedBy() != null ? loginIdsByUserId.get(purchase.getReviewedBy()) : null,
                purchase.getReviewedAt(),
                purchase.getCreatedAt()
        );
    }
}
