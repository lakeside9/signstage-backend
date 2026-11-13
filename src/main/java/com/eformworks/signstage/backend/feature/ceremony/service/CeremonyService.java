package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.billing.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.billing.entity.SalesChannel;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.PlatformAdminCeremonyDiscountDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyAssignment;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 마스터(Ceremony) 등록·조회·수정·삭제와 플랫폼 관리자의 행사 상태·건별 재량 할인 관리.
 * signstage-docs business/ceremony-feature-migration-review.md 4.1/4.6/4.7절 참고.
 *
 * <p>2026-11-11 분할(backend-package-structure-consistency-review.md 11.6절) — 접근 검사는
 * {@link CeremonyAccessService}, 카탈로그 판정·한도는 {@link CeremonyCatalogService}, 플랜
 * 선택·확정은 {@link CeremonyPlanService}, 예상 금액 계산은 {@link CeremonyQuoteService},
 * 장바구니·구매는 {@link CeremonyUnitProductPurchaseService}, 구매 승인 대기열은
 * {@link CeremonyUnitProductPurchaseApprovalService}로, DRAFT 행사 삭제(자식 데이터 연쇄 삭제)는
 * {@link CeremonyDeletionService}로 옮겼다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyService {

    private final CeremonyRepository ceremonyRepository;
    private final CeremonyAssignmentRepository ceremonyAssignmentRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final BillingCatalogQueryService billingCatalogQueryService;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;
    private final CeremonyAccessService ceremonyAccessService;
    private final CeremonyCatalogService ceremonyCatalogService;
    private final CeremonyPlanService ceremonyPlanService;

    /**
     * {@code billingPlanId}는 생략할 수 있다(2026-09-10, 사용자 요청 — signstage-docs
     * business/ceremony-registration-flow-and-billing-tab-separation-review.md) — 제목만
     * 먼저 등록하고 플랜은 나중에 {@link CeremonyPlanService#updatePlan}으로(첫 선택이든 교체든 그 메서드가 똑같이
     * 처리한다) 고를 수 있다. 생략하면 플랜 관련 검증·{@link CeremonyPlanHistory} 스냅샷을
     * 전부 건너뛰고 {@code billingPlan = null}인 DRAFT 행사만 만든다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary createCeremony(
            Long organizationId,
            Long currentUserId,
            CeremonyDto.Request.CreateCeremony request
    ) {
        Organization organization = findOrganizationOrThrow(organizationId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        checkCanCreateCeremony(actingMember);

        BillingPlan plan = null;
        BillingPlanDiscountPeriod planPeriod = null;
        // Ceremony.timeZoneId는 organization.getDefaultTimeZoneId()를 그대로 물려받는다 — 아직
        // Ceremony가 없으니 organization에서 같은 값을 미리 계산해 쓴다.
        LocalDate asOfDate = LocalDate.now(ZoneId.of(organization.getDefaultTimeZoneId()));
        if (request.getBillingPlanId() != null) {
            plan = billingCatalogQueryService.findBillingPlanOrThrow(request.getBillingPlanId(), SalesChannel.PARTNER_DIRECT);
            planPeriod = ceremonyCatalogService.resolveSellablePlanPeriod(plan, asOfDate);
            ceremonyCatalogService.checkCurrencyMatches(organization.getBillingCurrencyCode(), ceremonyCatalogService.resolvePlanCurrency(plan, asOfDate));
        }

        Ceremony ceremony = Ceremony.builder()
                .organization(organization)
                .billingPlan(plan)
                .title(request.getTitle())
                .description(request.getDescription())
                .organizingInstitution(request.getOrganizingInstitution())
                .organizingDepartment(request.getOrganizingDepartment())
                .contactName(request.getContactName())
                .contactTitle(request.getContactTitle())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .build();
        ceremony.updateSchedule(request.getLocation(), request.getStartsAt(), request.getEndsAt());
        ceremonyRepository.save(ceremony);
        if (plan != null) {
            ceremonyPlanService.recordPlanHistory(ceremony, plan, planPeriod, asOfDate);
        }

        // 생성자는 역할과 무관하게 자동으로 배정된다(4.7절) — 나중에 OPERATOR로 강등돼도
        // 본인이 만든 행사 접근권을 그대로 유지하는 부수 효과가 있다.
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        ceremonyAssignmentRepository.save(
                CeremonyAssignment.builder().ceremony(ceremony).user(creator).build()
        );

        return CeremonyDto.Response.CeremonySummary.from(ceremony);
    }

    /**
     * OPERATOR는 배정된 행사만 조회된다 — {@code assignedUserId}를 본인 id로 넘겨 조회 시점에
     * {@link CeremonyAssignment} 조인으로 스코핑한다({@code CeremonyRepositoryCustom#search}).
     */
    public Page<CeremonyDto.Response.CeremonySummary> findCeremonies(
            Long organizationId,
            Long currentUserId,
            String title,
            CeremonyStatus status,
            Pageable pageable
    ) {
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        Long assignedUserId = actingMember.getRole() == MemberRole.OPERATOR ? currentUserId : null;

        Page<Ceremony> ceremonies = ceremonyRepository.search(organizationId, title, status, assignedUserId, null, null, true, pageable);
        return ceremonies.map(CeremonyDto.Response.CeremonySummary::from);
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
        Page<Ceremony> ceremonies = ceremonyRepository.search(organizationId, title, status, null, null, null, false, pageable);
        return ceremonies.map(CeremonyDto.Response.CeremonySummary::from);
    }

    /**
     * 플랫폼 관리자용 단건 조회 — {@link #retrieveCeremony}와 달리 조직 멤버십을 요구하지 않는다
     * (위 {@link #findCeremoniesByPlatformAdmin}과 같은 이유). 행사 건별 재량 할인 상세 화면
     * (signstage-docs business/discount-management-screen-separation-review.md 6장 결정 #2)이
     * organizationId를 이미 알고 있는 상태(목록 행에서 넘어옴)로 호출한다.
     */
    public CeremonyDto.Response.CeremonySummary findCeremonyByPlatformAdmin(Long organizationId, Long ceremonyId) {
        return CeremonyDto.Response.CeremonySummary.from(ceremonyAccessService.findCeremonyInOrganizationForPlatformAdminOrThrow(organizationId, ceremonyId));
    }

    /**
     * 행사 건별 재량 할인 조직 횡단 목록 — {@code organizationId}/{@code status}/
     * {@code hasFinalDiscount}가 전부 선택 필터다(전부 null이면 전체). 조직명을 같이 내려줘야
     * 하는 화면이라 {@code CeremonyDto.Response.CeremonySummary}가 아니라 전용 DTO를 쓴다 —
     * signstage-docs business/discount-management-screen-separation-review.md 3.1절.
     */
    public Page<PlatformAdminCeremonyDiscountDto.Response.CeremonyDiscountSummary> findCeremonyDiscountsAcrossOrganizations(
            Long organizationId, CeremonyStatus status, Boolean hasFinalDiscount, Pageable pageable
    ) {
        Page<Ceremony> ceremonies = ceremonyRepository.search(organizationId, null, status, null, hasFinalDiscount, null, false, pageable);
        return ceremonies.map(ceremony -> new PlatformAdminCeremonyDiscountDto.Response.CeremonyDiscountSummary(
                ceremony.getId(),
                ceremony.getOrganization().getId(),
                ceremony.getOrganization().getName(),
                ceremony.getTitle(),
                ceremony.getStatus().name(),
                ceremony.getFinalDiscount().getDiscountType().name(),
                ceremony.getFinalDiscount().getDiscountValue(),
                ceremony.getCreatedAt()
        ));
    }

    /**
     * "이 플랜을 쓰는 행사" 조직 횡단 목록(signstage-docs
     * business/ceremony-plan-price-snapshot-consistency-review.md 3.5절, 2026-09-11) —
     * {@link #findCeremonyDiscountsAcrossOrganizations}와 같은 패턴이다. 카탈로그 관리자
     * 화면(항상 "오늘" 가격만 보여준다)만으로는 특정 행사가 실제로 어떤 값에 고정돼 있는지
     * 알 수 없다는 문제의 발견성 개선용 — 각 행에서 해당 행사의 "행사 이력" 화면(플랜 선택
     * 이력)으로 이어간다.
     */
    public Page<BillingPlanDto.Response.CeremonyUsingPlanSummary> findCeremoniesByBillingPlan(Long billingPlanId, Pageable pageable) {
        Page<Ceremony> ceremonies = ceremonyRepository.search(null, null, null, null, null, billingPlanId, false, pageable);
        return ceremonies.map(ceremony -> new BillingPlanDto.Response.CeremonyUsingPlanSummary(
                ceremony.getId(),
                ceremony.getOrganization().getId(),
                ceremony.getOrganization().getName(),
                ceremony.getTitle(),
                ceremony.getStatus().name(),
                ceremony.getCreatedAt()
        ));
    }

    public CeremonyDto.Response.CeremonySummary retrieveCeremony(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);
        return CeremonyDto.Response.CeremonySummary.from(ceremony);
    }

    /** 행사 수정 화면에서 이름/설명을 바꾼다. 플랜은 생성 시점에 고정이라 여기서 바꾸지 않는다. */
    @Transactional
    public CeremonyDto.Response.CeremonySummary updateCeremony(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyDto.Request.UpdateCeremony request
    ) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyAccessService.checkCeremonyEditable(ceremony);
        checkPlatformOrderContractFieldsUnchanged(ceremony, request);

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
        ceremony.updateSchedule(request.getLocation(), request.getStartsAt(), request.getEndsAt());
        return CeremonyDto.Response.CeremonySummary.from(ceremony);
    }

    /**
     * 플랫폼 관리자가 Ceremony 상태를 IN_PROGRESS/COMPLETED 사이에서 양방향으로 강제 변경한다
     * (실수로 완료됐거나 예외 상황 처리용). DRAFT는 대상이 아니다 — 플랜 확정(DRAFT →
     * IN_PROGRESS)은 {@link CeremonyPlanService#confirmPlan}의 단방향 전이로만 이뤄진다(signstage-docs
     * business/ceremony-plan-confirmation-review.md 4.4절). 플랫폼 관리자용 기능도 도메인
     * 서비스에 직접 둔다 — 관리자 컨트롤러는 도메인 feature에 {@code PlatformAdmin*} 이름으로
     * 두는 규칙(backend-coding-convention.md 3.4절, 여기서는 {@code PlatformAdminCeremonyController}).
     * 아래 {@code findCeremonyInOrganizationOrThrow}를 그대로 재사용한다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary updateStatusByPlatformAdmin(
            Long organizationId,
            Long ceremonyId,
            Long adminUserId,
            String actingPlatformRole,
            CeremonyDto.Request.UpdateStatus request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_CEREMONY_STATUS_CONTROL");

        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationForPlatformAdminOrThrow(organizationId, ceremonyId);
        CeremonyStatus previousStatus = ceremony.getStatus();
        CeremonyStatus newStatus = parseCeremonyStatus(request.getStatus());
        // 취소는 수주 "고객 취소"로만 들어가고 되돌리지 않는다(C01).
        if (newStatus == CeremonyStatus.DRAFT || newStatus == CeremonyStatus.CANCELLED) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        if (previousStatus == CeremonyStatus.CANCELLED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_CANCELLED);
        }
        ceremony.changeStatus(newStatus);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CEREMONY_STATUS, null, organizationId,
                "ceremonyId=" + ceremonyId + ", status: " + previousStatus + " -> " + newStatus
        );

        return CeremonyDto.Response.CeremonySummary.from(ceremony);
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
     * 4.2/4.4/6.2절 참고. 관리자 기능을 이 서비스에 직접 두는 이유는 위
     * {@link #updateStatusByPlatformAdmin}과 같다.
     */
    @Transactional
    public CeremonyDto.Response.CeremonySummary applyFinalDiscount(
            Long organizationId,
            Long ceremonyId,
            Long adminUserId,
            String actingPlatformRole,
            CeremonyDto.Request.ApplyFinalDiscount request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_CEREMONY_FINAL_DISCOUNT_MANAGE");

        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationForPlatformAdminOrThrow(organizationId, ceremonyId);
        checkCeremonyInProgress(ceremony);

        DiscountType previousType = ceremony.getFinalDiscount().getDiscountType();
        BigDecimal previousValue = ceremony.getFinalDiscount().getDiscountValue();
        DiscountType newType = parseDiscountType(request.getDiscountType());

        ceremony.applyFinalDiscount(newType, request.getDiscountValue());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CEREMONY_FINAL_DISCOUNT, null, organizationId,
                "ceremonyId=" + ceremonyId + ", finalDiscount: " + previousType + " " + previousValue
                        + " -> " + newType + " " + request.getDiscountValue()
        );

        return CeremonyDto.Response.CeremonySummary.from(ceremony);
    }

    /** DRAFT(플랜 미확정)·COMPLETED(완료) 둘 다 막고 IN_PROGRESS만 허용한다. */
    private void checkCeremonyInProgress(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        }
        if (ceremony.getStatus() == CeremonyStatus.COMPLETED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_ALREADY_COMPLETED);
        }
        if (ceremony.getStatus() == CeremonyStatus.CANCELLED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_CANCELLED);
        }
    }

    private DiscountType parseDiscountType(String discountType) {
        try {
            return DiscountType.valueOf(discountType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 플랫폼 수주 행사의 제목·장소·일정은 고객 계약 조건이라 파트너가 바꾸지 않는다(signstage-docs
     * business/platform-order-partner-subcontract-review.md D14). 운영 정보(설명, 주관 기관,
     * 담당자)는 그대로 고칠 수 있다.
     */
    private void checkPlatformOrderContractFieldsUnchanged(Ceremony ceremony, CeremonyDto.Request.UpdateCeremony request) {
        if (!ceremony.isPlatformOrder()) {
            return;
        }
        String requestedLocation = request.getLocation() == null || request.getLocation().isBlank()
                ? null
                : request.getLocation().trim();
        boolean changed = !Objects.equals(ceremony.getTitle(), request.getTitle())
                || !Objects.equals(ceremony.getLocation(), requestedLocation)
                || !Objects.equals(ceremony.getStartsAt(), request.getStartsAt())
                || !Objects.equals(ceremony.getEndsAt(), request.getEndsAt());
        if (changed) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLATFORM_ORDER_CONTRACT_FIELD_LOCKED);
        }
    }

    private void checkCanCreateCeremony(Member actingMember) {
        if (!rolePermissionService.isAllowed(actingMember.getRole().name(), "ACTION_CEREMONY_CREATE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    /**
     * 플랫폼 관리자 축(PLATFORM_SUPPORT/PLATFORM_OPS/PLATFORM_SUPER) 액션 권한 검사 —
     * {@code BillingPlanService#checkAllowed}와 같은 패턴. signstage-docs
     * business/organization-discount-override-security-and-validity-period-review.md 결정
     * #3(2026-09-08) — 행사 상태 강제 변경/행사 건별 재량 할인/구매요청 승인·반려를 하드코딩
     * 역할집합에서 동적 RBAC로 이관했다.
     */
    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
