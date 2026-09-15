package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.ceremony.dto.OrganizationSubscriptionDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationSubscription;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationSubscriptionHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationSubscriptionStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.SubscriptionType;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationSubscriptionHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationSubscriptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직 구독/계약 — signstage-docs business/organization-event-discount-pricing-review.md
 * 8장 결정(2026-09-10 착수 확정). 조직(OWNER)이 구독형 {@link BillingPlan}을 신청하면
 * 플랫폼 관리자(PLATFORM_OPS 이상)가 승인해야 실제로 사용할 수 있다(8.7-2 결정) — 중도
 * 해지도 같은 요청→승인 구조를 재사용한다(8.7-3 결정). 조직당 진행 중(PENDING/ACTIVE/
 * CANCELLATION_REQUESTED) 구독은 항상 최대 1건이며, 재계약(새 구독 승인)은 기존 ACTIVE
 * 행을 SUPERSEDED로 대체한다(8.3-2/8.4-1 결정). 기간 만료는 배치 스케줄러
 * ({@code OrganizationSubscriptionExpirationScheduler})가 매일 처리한다(8.7-2 결정) — 횟수
 * 소진은 {@link #consumeForCeremonyConfirmation}이 {@code CeremonyService#confirmPlan} 호출
 * 시점에 이벤트 기반으로 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationSubscriptionService {

    private static final List<OrganizationSubscriptionStatus> IN_PROGRESS_STATUSES = List.of(
            OrganizationSubscriptionStatus.PENDING, OrganizationSubscriptionStatus.CANCELLATION_REQUESTED
    );

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final OrganizationSubscriptionHistoryRepository subscriptionHistoryRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CeremonyRepository ceremonyRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;
    // "구매 비용" 스냅샷 계산용(2026-09-14 추가) — CeremonyService#calculateEstimatedTotal의
    // 플랜 소계 계산(단위 상품 소계 + 플랜 할인)과 같은 재료를 쓴다.
    private final BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    private final UnitProductPricePeriodRepository unitProductPricePeriodRepository;
    private final BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;
    private final MoneyCalculator moneyCalculator;

    // ==================== 조직(OWNER) 셀프서비스 ====================

    @Transactional
    public OrganizationSubscriptionDto.Response.SubscriptionSummary requestSubscription(
            Long organizationId,
            Long currentUserId,
            OrganizationSubscriptionDto.Request.CreateSubscription request
    ) {
        Organization organization = findOrganizationOrThrow(organizationId);
        checkOwner(organizationId, currentUserId);
        User requestedBy = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));

        BillingPlan plan = billingPlanRepository.findById(request.getBillingPlanId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND));
        if (!plan.isSubscription()) {
            throw new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_PLAN_NOT_SUBSCRIPTION_TYPE);
        }
        if (subscriptionRepository.existsByOrganizationIdAndStatusIn(organizationId, IN_PROGRESS_STATUSES)) {
            throw new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_ALREADY_IN_PROGRESS);
        }

        OrganizationSubscription subscription = OrganizationSubscription.builder()
                .organization(organization)
                .billingPlan(plan)
                .requestedBy(requestedBy)
                .build();
        subscriptionRepository.save(subscription);
        recordHistory(subscription, null, null);

        return toSummary(subscription);
    }

    @Transactional
    public OrganizationSubscriptionDto.Response.SubscriptionSummary requestCancellation(
            Long organizationId,
            Long currentUserId,
            OrganizationSubscriptionDto.Request.RequestCancellation request
    ) {
        checkOwner(organizationId, currentUserId);
        OrganizationSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatus(organizationId, OrganizationSubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_NOT_ACTIVE));

        subscription.requestCancellation(request.getCancellationReason());
        recordHistory(subscription, null, request.getCancellationReason());

        return toSummary(subscription);
    }

    /** 조직 아무 멤버나 조회 가능 — 진행 중이거나 사용 중인 구독이 없으면 null. */
    public OrganizationSubscriptionDto.Response.SubscriptionSummary findCurrentSubscription(
            Long organizationId, Long currentUserId
    ) {
        memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, currentUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));

        List<OrganizationSubscription> current = subscriptionRepository.findAllByOrganizationIdAndStatusIn(
                organizationId,
                List.of(
                        OrganizationSubscriptionStatus.PENDING,
                        OrganizationSubscriptionStatus.ACTIVE,
                        OrganizationSubscriptionStatus.CANCELLATION_REQUESTED
                )
        );
        return current.stream().findFirst().map(this::toSummary).orElse(null);
    }

    /**
     * 조직의 구독 신청 이력 조회, 페이지네이션+상태 필터(2026-09-14 추가, 같은 날 후속으로
     * 목록형+검색+페이지네비게이션으로 전환) — signstage-docs
     * business/subscription-margin-screen-separation-review.md 후속(사용자 요청 — "구독
     * 화면은 목록형태로 만들고, 검색영역/목록/페이지 네비게이션을 적용해주세요").
     * {@link #findCurrentSubscription}은 진행 중/사용중인 것 하나만 돌려주지만, 이건
     * PENDING/REJECTED로 끝난 옛 신청·재계약으로 SUPERSEDED된 옛 계약까지 전부 최신순으로
     * 돌려준다. 이름을 {@code findHistory}가 아니라 {@code findSubscriptions}로 지은 건
     * {@link #findHistory(Long)}(구독 하나의 상태 전이 이력, 관리자 전용)와 이름이 겹쳐
     * 혼동되는 걸 피하려는 것이다 — 같은 서비스 안에 "구독 여러 건 목록"과 "구독 하나의
     * 이력"이라는 서로 다른 개념이 둘 다 있다. 조회 권한은 현재 구독 조회와 같다(조직 멤버
     * 누구나). `status`를 생략하면 전체를 반환한다(관리자 `findRequests`와 같은 패턴).
     */
    public Page<OrganizationSubscriptionDto.Response.SubscriptionSummary> findSubscriptions(
            Long organizationId, Long currentUserId, OrganizationSubscriptionStatus status, Pageable pageable
    ) {
        memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, currentUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));

        Page<OrganizationSubscription> page = status != null
                ? subscriptionRepository.findAllByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, status, pageable)
                : subscriptionRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
        return page.map(this::toSummary);
    }

    // ==================== 플랫폼 관리자 ====================

    @Transactional
    public OrganizationSubscriptionDto.Response.SubscriptionSummary approve(
            Long subscriptionId, Long adminUserId, String actingPlatformRole
    ) {
        checkCanManage(actingPlatformRole);
        OrganizationSubscription subscription = findPendingOrThrow(subscriptionId);
        Organization organization = subscription.getOrganization();
        BillingPlan plan = subscription.getBillingPlan();

        LocalDate startDate = LocalDate.now(ZoneId.of(organization.getDefaultTimeZoneId()));
        LocalDate endDate = plan.getSubscriptionType() == SubscriptionType.PERIOD_AND_COUNT
                ? startDate.plusMonths(plan.getSubscriptionPeriodMonths())
                : null;

        // 재계약 — 이미 ACTIVE인 구독이 있으면 이 승인으로 대체한다(8.3-2/8.4-1 결정).
        subscriptionRepository.findByOrganizationIdAndStatus(organization.getId(), OrganizationSubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.supersede(adminUserId);
                    recordHistory(existing, adminUserId, null);
                });

        PlanPriceSnapshot priceSnapshot = calculatePlanPriceSnapshot(plan, startDate);
        subscription.approve(adminUserId, startDate, endDate, priceSnapshot.amount(), priceSnapshot.currencyCode());
        recordHistory(subscription, adminUserId, null);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.APPROVE_ORGANIZATION_SUBSCRIPTION, null, organization.getId(),
                "subscriptionId=" + subscriptionId + ", planId=" + plan.getId()
        );
        return toSummary(subscription);
    }

    private record PlanPriceSnapshot(BigDecimal amount, String currencyCode) {
    }

    /**
     * 구독 승인 시점 "구매 비용"(플랜 소계 - 할인) 계산(2026-09-14 추가) — signstage-docs
     * business/subscription-margin-screen-separation-review.md 6장 후속. 재료·공식은
     * {@code CeremonyService#calculateEstimatedTotal}의 플랜 소계 계산과 같다(포함 단위 상품마다
     * {@code asOfDate} 기준 유효 가격 기간의 판매가 × 포함 수량을 더한 뒤, 플랜의 {@code
     * asOfDate} 기준 유효 할인 기간을 한 번만 적용). {@code BillingPlan}은 더는 자기 가격을
     * 갖지 않아(엔티티 주석 참고) 이 계산을 매번 다시 해야 한다 — 단, 구독은 승인 시점에 한
     * 번만 계산해 고정하고(다른 스냅샷 4개와 동일), 행사처럼 재계산하지 않는다. 통화 코드는
     * 플랜의 첫 포함 단위 상품 가격 기간에서 가져온다(프런트 `AdminBillingPlanList.tsx`가
     * "단위 상품 소계" 표시에 쓰는 것과 같은 방식) — 통화 불일치는 카탈로그 등록 단계에서
     * 이미 다루는 문제라 여기서 다시 검증하지 않는다.
     */
    private PlanPriceSnapshot calculatePlanPriceSnapshot(BillingPlan plan, LocalDate asOfDate) {
        List<BillingPlanUnitProduct> lines = billingPlanUnitProductRepository.findAllByBillingPlanId(plan.getId());

        BigDecimal subtotal = BigDecimal.ZERO;
        String currencyCode = null;
        for (BillingPlanUnitProduct line : lines) {
            if (line.getIncludedQuantity() <= 0) {
                continue;
            }
            Optional<UnitProductPricePeriod> period =
                    unitProductPricePeriodRepository.findEffective(line.getUnitProduct().getId(), asOfDate);
            if (period.isEmpty()) {
                continue;
            }
            if (currencyCode == null) {
                currencyCode = period.get().getPriceInfo().getCurrencyCode();
            }
            subtotal = subtotal.add(
                    period.get().getPriceInfo().getSalePrice().multiply(BigDecimal.valueOf(line.getIncludedQuantity()))
            );
        }
        if (currencyCode == null) {
            currencyCode = InternationalizationDefaults.CURRENCY_CODE;
        }
        CurrencyPolicy currencyPolicy = new CurrencyPolicy(
                currencyCode, Currency.getInstance(currencyCode).getDefaultFractionDigits(), RoundingMode.HALF_UP
        );

        DiscountType discountType = billingPlanDiscountPeriodRepository.findEffective(plan.getId(), asOfDate)
                .map(period -> period.getDiscount().getDiscountType())
                .orElse(DiscountType.PERCENT);
        BigDecimal discountValue = billingPlanDiscountPeriodRepository.findEffective(plan.getId(), asOfDate)
                .map(period -> period.getDiscount().getDiscountValue())
                .orElse(BigDecimal.ZERO);

        BigDecimal amount = moneyCalculator.applyDiscount(subtotal, discountType, discountValue, currencyPolicy);
        return new PlanPriceSnapshot(amount, currencyCode);
    }

    @Transactional
    public OrganizationSubscriptionDto.Response.SubscriptionSummary reject(
            Long subscriptionId,
            Long adminUserId,
            String actingPlatformRole,
            OrganizationSubscriptionDto.Request.Reject request
    ) {
        checkCanManage(actingPlatformRole);
        OrganizationSubscription subscription = findPendingOrThrow(subscriptionId);

        subscription.reject(adminUserId, request.getRejectionReason());
        recordHistory(subscription, adminUserId, request.getRejectionReason());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.REJECT_ORGANIZATION_SUBSCRIPTION, null,
                subscription.getOrganization().getId(),
                "subscriptionId=" + subscriptionId + ", reason=" + request.getRejectionReason()
        );
        return toSummary(subscription);
    }

    @Transactional
    public OrganizationSubscriptionDto.Response.SubscriptionSummary approveCancellation(
            Long subscriptionId, Long adminUserId, String actingPlatformRole
    ) {
        checkCanManage(actingPlatformRole);
        OrganizationSubscription subscription = findCancellationRequestedOrThrow(subscriptionId);

        subscription.approveCancellation(adminUserId);
        recordHistory(subscription, adminUserId, null);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.APPROVE_ORGANIZATION_SUBSCRIPTION_CANCELLATION, null,
                subscription.getOrganization().getId(), "subscriptionId=" + subscriptionId
        );
        return toSummary(subscription);
    }

    @Transactional
    public OrganizationSubscriptionDto.Response.SubscriptionSummary rejectCancellation(
            Long subscriptionId,
            Long adminUserId,
            String actingPlatformRole,
            OrganizationSubscriptionDto.Request.Reject request
    ) {
        checkCanManage(actingPlatformRole);
        OrganizationSubscription subscription = findCancellationRequestedOrThrow(subscriptionId);

        subscription.rejectCancellation(adminUserId, request.getRejectionReason());
        recordHistory(subscription, adminUserId, request.getRejectionReason());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.REJECT_ORGANIZATION_SUBSCRIPTION_CANCELLATION, null,
                subscription.getOrganization().getId(),
                "subscriptionId=" + subscriptionId + ", reason=" + request.getRejectionReason()
        );
        return toSummary(subscription);
    }

    /** 관리자 승인 대기열 — 조회는 PLATFORM_SUPPORT 이상(컨트롤러에서 메뉴 권한으로 검사). */
    public Page<OrganizationSubscriptionDto.Response.SubscriptionSummary> findRequests(
            OrganizationSubscriptionStatus status, Pageable pageable
    ) {
        Page<OrganizationSubscription> page = status != null
                ? subscriptionRepository.findAllByStatus(status, pageable)
                : subscriptionRepository.findAll(pageable);
        return page.map(this::toSummary);
    }

    public List<OrganizationSubscriptionDto.Response.SubscriptionHistorySummary> findHistory(Long subscriptionId) {
        return subscriptionHistoryRepository.findAllByOrganizationSubscriptionIdOrderByCreatedAtDesc(subscriptionId).stream()
                .map(h -> new OrganizationSubscriptionDto.Response.SubscriptionHistorySummary(
                        h.getId(), h.getStatus().name(), h.getReviewedBy(), h.getNote(), h.getCreatedBy(), h.getCreatedAt()
                ))
                .toList();
    }

    // ==================== Ceremony 연동 ====================

    /**
     * {@code CeremonyService#confirmPlan}이 플랜 확정 직후 호출한다. 구독형 플랜이 아니면 아무
     * 일도 하지 않는다. 구독형이면 그 조직의 ACTIVE 구독(이 플랜과 일치해야 함)을 찾아 이
     * Ceremony에 연결하고, 그 결과 잔여 횟수가 0이 되면 곧바로 EXHAUSTED로 전이한다(같은
     * 트랜잭션 — 8.2-9번/8.6-A-3 결정).
     */
    @Transactional
    public void consumeForCeremonyConfirmation(Ceremony ceremony) {
        BillingPlan plan = ceremony.getBillingPlan();
        if (plan == null || !plan.isSubscription()) {
            return;
        }

        OrganizationSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatus(ceremony.getOrganization().getId(), OrganizationSubscriptionStatus.ACTIVE)
                .filter(s -> s.getBillingPlan().getId().equals(plan.getId()))
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_REQUIRED));

        long used = ceremonyRepository.countBySubscriptionId(subscription.getId());
        int remaining = subscription.getAllowedCountSnapshot() - (int) used;
        if (remaining <= 0) {
            throw new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_EXHAUSTED);
        }

        ceremony.linkSubscription(subscription);
        if (remaining - 1 <= 0) {
            subscription.exhaust();
            recordHistory(subscription, null, null);
        }
    }

    // ==================== 배치 스케줄러 진입점 ====================

    /**
     * {@code OrganizationSubscriptionExpirationScheduler}가 매일 호출한다 — PERIOD_AND_COUNT
     * 구독 중 종료일이 지난(오늘 이전) ACTIVE 건을 EXPIRED로 전이한다(8.7-2 결정, 배치 스케줄러
     * 신설). 플랫폼 기본 타임존(Asia/Seoul) 기준 "오늘"을 쓴다 — 배치 실행 시점 판정이라
     * 조직별 타임존까지 정밀하게 나누지 않는다.
     */
    @Transactional
    public int expireOverdueSubscriptions() {
        LocalDate today = InternationalizationDefaults.today();
        List<OrganizationSubscription> overdue = subscriptionRepository.findAllByStatusAndEndDateBefore(
                OrganizationSubscriptionStatus.ACTIVE, today
        );
        overdue.forEach(subscription -> {
            subscription.expire();
            recordHistory(subscription, null, null);
        });
        return overdue.size();
    }

    // ==================== 내부 헬퍼 ====================

    private void checkOwner(Long organizationId, Long userId) {
        Member member = memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        if (member.getRole() != MemberRole.OWNER) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void checkCanManage(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_SUBSCRIPTION_REQUEST_REVIEW")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private OrganizationSubscription findPendingOrThrow(Long subscriptionId) {
        OrganizationSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (subscription.getStatus() != OrganizationSubscriptionStatus.PENDING) {
            throw new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_NOT_PENDING);
        }
        return subscription;
    }

    private OrganizationSubscription findCancellationRequestedOrThrow(Long subscriptionId) {
        OrganizationSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (subscription.getStatus() != OrganizationSubscriptionStatus.CANCELLATION_REQUESTED) {
            throw new ApplicationException(CeremonyErrorCode.SUBSCRIPTION_NOT_CANCELLATION_REQUESTED);
        }
        return subscription;
    }

    private void recordHistory(OrganizationSubscription subscription, Long reviewedBy, String note) {
        subscriptionHistoryRepository.save(OrganizationSubscriptionHistory.builder()
                .organizationSubscription(subscription)
                .status(subscription.getStatus())
                .reviewedBy(reviewedBy)
                .note(note)
                .build());
    }

    private OrganizationSubscriptionDto.Response.SubscriptionSummary toSummary(OrganizationSubscription subscription) {
        Integer usedCount = subscription.getAllowedCountSnapshot() != null
                ? (int) ceremonyRepository.countBySubscriptionId(subscription.getId())
                : null;
        Integer remainingCount = usedCount != null ? subscription.getAllowedCountSnapshot() - usedCount : null;
        String reviewerLoginId = subscription.getReviewedBy() != null
                ? userRepository.findById(subscription.getReviewedBy()).map(User::getLoginId).orElse(null)
                : null;

        return new OrganizationSubscriptionDto.Response.SubscriptionSummary(
                subscription.getId(),
                subscription.getOrganization().getId(),
                subscription.getOrganization().getName(),
                subscription.getBillingPlan().getId(),
                subscription.getBillingPlan().getName(),
                subscription.getStatus().name(),
                subscription.getRequestedBy().getLoginId(),
                subscription.getPlanNameSnapshot(),
                subscription.getSubscriptionTypeSnapshot() != null ? subscription.getSubscriptionTypeSnapshot().name() : null,
                subscription.getPeriodMonthsSnapshot(),
                subscription.getAllowedCountSnapshot(),
                subscription.getPurchaseAmountSnapshot(),
                subscription.getCurrencyCodeSnapshot(),
                usedCount,
                remainingCount,
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getApprovalSource().name(),
                reviewerLoginId,
                subscription.getReviewedAt(),
                subscription.getRejectionReason(),
                subscription.getCancellationReason(),
                subscription.getCreatedAt()
        );
    }
}
