package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.OrderInspectionDto;
import com.eformworks.signstage.backend.feature.order.dto.PartnerOffsetDto;
import com.eformworks.signstage.backend.feature.order.dto.PartnerPayoutDto;
import com.eformworks.signstage.backend.feature.order.entity.OrderInspection;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffset;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffsetStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayout;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayoutStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.OrderInspectionRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOffsetRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerPayoutRepository;
import com.eformworks.signstage.backend.feature.organization.dto.PartnerSubcontractProfileDto;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.service.PartnerSubcontractProfileService;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파트너 월 합산 지급 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 6단계,
 * D11(월 합산·지급 기한), D12(고객 입금과 무관하게 지급).
 *
 * <ul>
 *   <li>지난달까지의 승인월을 골라 정산하면, 그 달에 승인됐고 아직 지급에 묶이지 않은 검수를 파트너별로
 *       합산해 지급 건(CONFIRMED)을 만든다. 정산 계좌는 이때 스냅샷한다. 같은 달을 다시 정산하면 그
 *       사이 승인된 건만 새 지급 건이 된다.</li>
 *   <li>실제 이체는 시스템 밖에서 하고, 이체 후 지급 완료(PAID)로 기록하면 원장에 지급(−)을 남긴다.</li>
 *   <li>이체 전에는 취소해 검수 건을 풀 수 있다(다시 정산하면 새 지급 건으로 묶인다).</li>
 *   <li>정산월 말일까지 생긴 대기 귀책 상계를 지급액에서 뺀다. 상계가 검수 합계를 넘으면 지급액은 0이고
 *       넘는 만큼은 이월 상계로 다음 정산에서 뺀다(D13). 이전 달에 이의 기간·이의 제기로 빠졌던 검수도
 *       이후 정산에 들어간다.</li>
 *   <li>지급액이 기준 금액 이상이면 PLATFORM_SUPER가 승인해야 지급 완료로 기록할 수 있고, 정산을 만든
 *       사람은 승인할 수 없다(D19, {@code ACTION_PARTNER_PAYOUT_LARGE_APPROVE}).</li>
 * </ul>
 *
 * <p>정산·지급 처리는 {@code ACTION_PARTNER_PAYMENT_APPROVE}(PLATFORM_OPS 이상), 파트너의 조회는
 * {@code ACTION_PARTNER_PAYOUT_VIEW}(OWNER·ADMIN)가 필요하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerPayoutService {

    private final PartnerPayoutRepository partnerPayoutRepository;
    private final OrderInspectionRepository orderInspectionRepository;
    private final PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    private final OrderInspectionService orderInspectionService;
    private final PartnerOffsetRepository partnerOffsetRepository;
    private final PartnerSettlementPolicy partnerSettlementPolicy;
    private final PartnerPaymentControlPolicy partnerPaymentControlPolicy;
    private final PartnerSubcontractProfileService partnerSubcontractProfileService;
    private final MemberRepository memberRepository;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public Page<PartnerPayoutDto.Response.PayoutSummary> findPayouts(
            String settlementMonth,
            PartnerPayoutStatus status,
            Long organizationId,
            Pageable pageable
    ) {
        return partnerPayoutRepository.search(settlementMonth, status, organizationId, pageable)
                .map(PartnerPayoutDto.Response.PayoutSummary::from);
    }

    public PartnerPayoutDto.Response.PayoutDetail retrievePayout(Long partnerPayoutId) {
        return toDetail(findPayoutOrThrow(partnerPayoutId));
    }

    public PartnerPayoutDto.Response.Ledger retrieveLedger(Long organizationId, Pageable pageable) {
        return new PartnerPayoutDto.Response.Ledger(
                partnerLedgerEntryRepository.sumAmountByOrganizationId(organizationId),
                PageResponse.from(partnerLedgerEntryRepository
                        .findAllByOrganizationIdOrderByOccurredOnDescIdDesc(organizationId, pageable)
                        .map(PartnerPayoutDto.Response.LedgerEntryItem::from))
        );
    }

    @Transactional
    public List<PartnerPayoutDto.Response.PayoutSummary> createPayouts(
            String settlementMonth,
            Long adminUserId,
            String actingPlatformRole
    ) {
        checkCanApprovePayment(actingPlatformRole);
        YearMonth month = YearMonth.parse(settlementMonth);
        if (!month.isBefore(YearMonth.now(SalesOrderService.PLATFORM_ZONE))) {
            throw new ApplicationException(OrderErrorCode.PAYOUT_MONTH_NOT_CLOSED);
        }
        orderInspectionService.syncInspections();
        LocalDate today = LocalDate.now(SalesOrderService.PLATFORM_ZONE);
        LocalDate monthEnd = month.atEndOfMonth();

        Map<String, List<OrderInspection>> groups = new LinkedHashMap<>();
        for (OrderInspection inspection : orderInspectionRepository.findSettleableUntil(
                monthEnd, today.minusDays(partnerPaymentControlPolicy.disputeDays()))) {
            String key = inspection.getOrganization().getId() + ":" + inspection.getPartnerOrder().getCurrencyCode();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(inspection);
        }

        List<PartnerPayoutDto.Response.PayoutSummary> created = new ArrayList<>();
        for (List<OrderInspection> items : groups.values()) {
            OrderInspection first = items.getFirst();
            Long organizationId = first.getOrganization().getId();
            String currencyCode = first.getPartnerOrder().getCurrencyCode();
            BigDecimal gross = items.stream().map(OrderInspection::getPayableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<PartnerOffset> offsets = partnerOffsetRepository.findPendingForSettlement(organizationId, currencyCode, monthEnd);
            BigDecimal offsetTotal = offsets.stream().map(PartnerOffset::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal appliedOffset = offsetTotal.min(gross);
            BigDecimal amount = gross.subtract(appliedOffset);
            LocalDate earliestApprovedDate = items.stream().map(OrderInspection::getApprovedDate).min(LocalDate::compareTo).orElseThrow();
            PartnerSubcontractProfileDto.Response.Profile profile =
                    partnerSubcontractProfileService.retrieveProfileByPlatformAdmin(organizationId);

            PartnerPayout payout = partnerPayoutRepository.save(PartnerPayout.builder()
                    .organization(first.getOrganization())
                    .settlementMonth(settlementMonth)
                    .currencyCode(currencyCode)
                    .grossAmount(gross)
                    .offsetAmount(appliedOffset)
                    .dueDate(partnerSettlementPolicy.paymentDueDate(month, earliestApprovedDate))
                    .bankName(profile.getBankName())
                    .bankAccountNumber(profile.getBankAccountNumber())
                    .accountHolderName(profile.getAccountHolderName())
                    .approvalRequired(partnerPaymentControlPolicy.requiresPayoutApproval(amount))
                    .build());
            items.forEach(inspection -> inspection.assignPayout(payout));
            offsets.forEach(offset -> offset.applyTo(payout));
            if (offsetTotal.compareTo(gross) > 0) {
                partnerOffsetRepository.save(offsets.getLast().carryOver(offsetTotal.subtract(gross), payout, monthEnd));
            }

            auditLogRecorder.record(adminUserId, PlatformAdminAction.CREATE_PARTNER_PAYOUTS, null, organizationId,
                    "partnerPayoutId=" + payout.getId() + ", settlementMonth=" + settlementMonth
                            + ", grossAmount=" + gross + ", offsetAmount=" + appliedOffset + ", amount=" + amount
                            + ", inspections=" + items.size() + ", approvalRequired=" + payout.isApprovalRequired());
            created.add(PartnerPayoutDto.Response.PayoutSummary.from(payout));
        }
        return created;
    }

    @Transactional
    public PartnerPayoutDto.Response.PayoutDetail markPaid(
            Long partnerPayoutId,
            Long adminUserId,
            String actingPlatformRole,
            PartnerPayoutDto.Request.MarkPaid request
    ) {
        checkCanApprovePayment(actingPlatformRole);
        PartnerPayout payout = findConfirmedOrThrow(partnerPayoutId);
        if (payout.isAwaitingApproval()) {
            throw new ApplicationException(OrderErrorCode.PAYOUT_APPROVAL_REQUIRED);
        }
        if (request.getPaidDate().isAfter(LocalDate.now(SalesOrderService.PLATFORM_ZONE))) {
            throw new ApplicationException(OrderErrorCode.PAYOUT_PAID_DATE_INVALID);
        }
        payout.markPaid(adminUserId, request.getPaidDate(), request.getTransferredAmount(), trimToNull(request.getPaymentMemo()));
        partnerLedgerEntryRepository.save(PartnerLedgerEntry.payment(payout));

        auditLogRecorder.record(adminUserId, PlatformAdminAction.MARK_PARTNER_PAYOUT_PAID, null,
                payout.getOrganization().getId(),
                "partnerPayoutId=" + partnerPayoutId + ", paidDate=" + request.getPaidDate()
                        + ", transferredAmount=" + request.getTransferredAmount());
        return toDetail(payout);
    }

    @Transactional
    public PartnerPayoutDto.Response.PayoutDetail cancelPayout(
            Long partnerPayoutId,
            Long adminUserId,
            String actingPlatformRole,
            String reason
    ) {
        checkCanApprovePayment(actingPlatformRole);
        PartnerPayout payout = findConfirmedOrThrow(partnerPayoutId);
        List<PartnerOffset> carriedOver = partnerOffsetRepository.findAllByCarriedOverFromPayoutId(partnerPayoutId);
        if (carriedOver.stream().anyMatch(offset -> offset.getStatus() == PartnerOffsetStatus.APPLIED)) {
            throw new ApplicationException(OrderErrorCode.PAYOUT_CARRY_OVER_APPLIED);
        }
        List<OrderInspection> items = orderInspectionRepository.findAllByPartnerPayoutId(partnerPayoutId);
        items.forEach(OrderInspection::releasePayout);
        partnerOffsetRepository.findAllByPartnerPayoutId(partnerPayoutId).forEach(PartnerOffset::release);
        carriedOver.stream().filter(PartnerOffset::isPending).forEach(offset -> offset.cancel("지급 건 취소로 이월 취소"));
        payout.cancel(reason.trim());

        auditLogRecorder.record(adminUserId, PlatformAdminAction.CANCEL_PARTNER_PAYOUT, null,
                payout.getOrganization().getId(), "partnerPayoutId=" + partnerPayoutId + ", reason=" + reason);
        return new PartnerPayoutDto.Response.PayoutDetail(
                PartnerPayoutDto.Response.PayoutSummary.from(payout), List.of(), List.of()
        );
    }

    /** 고액 지급 승인(D19) — PLATFORM_SUPER만, 정산을 만든 사람은 승인할 수 없다. */
    @Transactional
    public PartnerPayoutDto.Response.PayoutDetail approvePayout(
            Long partnerPayoutId,
            Long adminUserId,
            String actingPlatformRole
    ) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_PARTNER_PAYOUT_LARGE_APPROVE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        PartnerPayout payout = findConfirmedOrThrow(partnerPayoutId);
        if (!payout.isAwaitingApproval()) {
            throw new ApplicationException(OrderErrorCode.PAYOUT_APPROVAL_NOT_NEEDED);
        }
        if (Objects.equals(payout.getCreatedBy(), adminUserId)) {
            throw new ApplicationException(OrderErrorCode.PAYOUT_APPROVER_IS_CREATOR);
        }
        payout.approve(adminUserId);

        auditLogRecorder.record(adminUserId, PlatformAdminAction.APPROVE_PARTNER_PAYOUT, null,
                payout.getOrganization().getId(), "partnerPayoutId=" + partnerPayoutId + ", amount=" + payout.getAmount());
        return toDetail(payout);
    }

    public Page<PartnerPayoutDto.Response.PayoutSummary> findReceivedPayouts(
            Long organizationId,
            Long currentUserId,
            Pageable pageable
    ) {
        checkCanViewReceived(organizationId, currentUserId);
        return findPayouts(null, null, organizationId, pageable);
    }

    public PartnerPayoutDto.Response.PayoutDetail retrieveReceivedPayout(
            Long organizationId,
            Long partnerPayoutId,
            Long currentUserId
    ) {
        checkCanViewReceived(organizationId, currentUserId);
        PartnerPayout payout = partnerPayoutRepository.findByIdAndOrganizationId(partnerPayoutId, organizationId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PAYOUT_NOT_FOUND));
        return toDetail(payout, false);
    }

    public PartnerPayoutDto.Response.Ledger retrieveReceivedLedger(
            Long organizationId,
            Long currentUserId,
            Pageable pageable
    ) {
        checkCanViewReceived(organizationId, currentUserId);
        return retrieveLedger(organizationId, pageable);
    }

    private PartnerPayoutDto.Response.PayoutDetail toDetail(PartnerPayout payout) {
        return toDetail(payout, true);
    }

    /** @param forPlatform false면 파트너용이라 수행 평점을 뺀다. */
    private PartnerPayoutDto.Response.PayoutDetail toDetail(PartnerPayout payout, boolean forPlatform) {
        LocalDate today = LocalDate.now(SalesOrderService.PLATFORM_ZONE);
        int disputeDays = partnerPaymentControlPolicy.disputeDays();
        List<OrderInspectionDto.Response.InspectionSummary> items = orderInspectionRepository
                .findAllByPartnerPayoutId(payout.getId()).stream()
                .map(inspection -> forPlatform
                        ? OrderInspectionDto.Response.InspectionSummary.from(inspection, today, disputeDays)
                        : OrderInspectionDto.Response.InspectionSummary.forPartner(inspection, today, disputeDays))
                .toList();
        List<PartnerOffsetDto.Response.OffsetSummary> offsets = partnerOffsetRepository
                .findAllByPartnerPayoutId(payout.getId()).stream()
                .map(PartnerOffsetDto.Response.OffsetSummary::from)
                .toList();
        return new PartnerPayoutDto.Response.PayoutDetail(PartnerPayoutDto.Response.PayoutSummary.from(payout), items, offsets);
    }

    private PartnerPayout findPayoutOrThrow(Long partnerPayoutId) {
        return partnerPayoutRepository.findById(partnerPayoutId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PAYOUT_NOT_FOUND));
    }

    private PartnerPayout findConfirmedOrThrow(Long partnerPayoutId) {
        PartnerPayout payout = findPayoutOrThrow(partnerPayoutId);
        if (!payout.isConfirmed()) {
            throw new ApplicationException(OrderErrorCode.PAYOUT_NOT_CONFIRMED);
        }
        return payout;
    }

    private void checkCanApprovePayment(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_PARTNER_PAYMENT_APPROVE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void checkCanViewReceived(Long organizationId, Long currentUserId) {
        Member member = memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, currentUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        if (!rolePermissionService.isAllowed(member.getRole().name(), "ACTION_PARTNER_PAYOUT_VIEW")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
