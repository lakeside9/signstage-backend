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
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyCapacityPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOptionalFeaturePurchaseRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanOptionalFeatureRepository billingPlanOptionalFeatureRepository;
    private final CapacityAddOnRepository capacityAddOnRepository;
    private final OptionalFeatureRepository optionalFeatureRepository;
    private final UserRepository userRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

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

        CeremonyCapacityPurchase purchase = CeremonyCapacityPurchase.builder()
                .ceremony(ceremony)
                .capacityAddOn(addOn)
                .quantity(request.getQuantity())
                .purchasedSalePrice(addOn.getSalePrice())
                .purchasedDiscountType(addOn.getDiscountType())
                .purchasedDiscountValue(addOn.getDiscountValue())
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

        CeremonyOptionalFeaturePurchase purchase = CeremonyOptionalFeaturePurchase.builder()
                .ceremony(ceremony)
                .optionalFeature(feature)
                .purchasedSalePrice(feature.getSalePrice())
                .purchasedDiscountType(feature.getDiscountType())
                .purchasedDiscountValue(feature.getDiscountValue())
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
        return optionalFeatureRepository.findAllById(availableIds).stream()
                .map(feature -> new OptionalFeatureDto.Response.OptionalFeatureSummary(
                        feature.getId(),
                        feature.getCode().name(),
                        feature.getName(),
                        feature.getSupplyPrice(),
                        feature.getSalePrice(),
                        feature.getDiscountType().name(),
                        feature.getDiscountValue(),
                        feature.isActive(),
                        feature.getCreatedAt()
                ))
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

    /** Ceremony 생성 시(최초 플랜 선택)와 {@link #changePlan}에서 매 변경마다 호출한다(3.4절). */
    private void recordPlanHistory(Ceremony ceremony, BillingPlan plan) {
        ceremonyPlanHistoryRepository.save(
                CeremonyPlanHistory.builder().ceremony(ceremony).billingPlan(plan).build()
        );
    }

    /**
     * 서명자/문서양식/테스트·본행사 등록 화면이 "등록할 수 있는 개수"를 보여주는 데 쓴다 —
     * {@link #calculateEffectiveCapacity}(플랜 기본값 + 승인된 추가구매)를 네 가지 용량 유형
     * 전부에 대해 계산해 돌려준다. 플랜이 없는 행사는 무제한이라 Integer.MAX_VALUE를 그대로
     * 돌려준다(프런트가 "무제한"으로 표시).
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
                calculateEffectiveCapacity(ceremony, CapacityType.MAIN_EVENTS)
        );
    }

    /**
     * 필수옵션(용량) 유효 한도 = 플랜 기본값 + Σ(구매수량 × addon.unitAmount). 플랜이 없는
     * 행사(4.8 예외 — 이 기능 배포 전 기존 행사)는 한도 강제 자체를 적용하지 않는다(사실상 무제한).
     */
    int calculateEffectiveCapacity(Ceremony ceremony, CapacityType capacityType) {
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan == null) {
            return Integer.MAX_VALUE;
        }

        int baseValue = switch (capacityType) {
            case SIGNERS -> plan.getMaxSigners();
            case TEMPLATES -> plan.getMaxTemplates();
            case TEST_EVENTS -> plan.getMaxTestEvents();
            case MAIN_EVENTS -> plan.getMaxMainEvents();
        };

        // 승인(APPROVED)된 요청만 한도에 반영한다 — 대기중/반려된 요청은 아직/영영 쓸 수 없다.
        int purchasedAmount = ceremonyCapacityPurchaseRepository
                .findAllByCeremonyIdAndCapacityAddOn_CapacityTypeAndStatus(ceremony.getId(), capacityType, PurchaseStatus.APPROVED)
                .stream()
                .mapToInt(purchase -> purchase.getQuantity() * purchase.getCapacityAddOn().getUnitAmount())
                .sum();

        return baseValue + purchasedAmount;
    }

    /**
     * Ceremony가 "구매한"(플랜 기본 포함 또는 추가구매) 선택옵션 id 집합(4.11절). 추가구매 쪽은
     * 승인(APPROVED)된 요청만 포함한다 — 대기중/반려된 요청은 아직/영영 적용할 수 없다.
     */
    List<Long> retrievePurchasedOptionalFeatureIds(Ceremony ceremony) {
        List<Long> purchased = ceremonyOptionalFeaturePurchaseRepository
                .findAllByCeremonyIdAndStatus(ceremony.getId(), PurchaseStatus.APPROVED).stream()
                .map(purchase -> purchase.getOptionalFeature().getId())
                .toList();
        if (ceremony.getBillingPlan() == null) {
            return purchased;
        }
        List<Long> includedInPlan = billingPlanOptionalFeatureRepository
                .findAllByBillingPlanId(ceremony.getBillingPlan().getId()).stream()
                .map(mapping -> mapping.getOptionalFeature().getId())
                .toList();
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
