package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.OrderInspectionDto;
import com.eformworks.signstage.backend.feature.order.entity.InspectionDeductionType;
import com.eformworks.signstage.backend.feature.order.entity.InspectionDisputeStatus;
import com.eformworks.signstage.backend.feature.order.entity.InspectionStatus;
import com.eformworks.signstage.backend.feature.order.entity.OrderInspection;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.OrderInspectionRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발주 검수 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 6단계, D11·D13.
 *
 * <ul>
 *   <li>수락한 발주의 행사가 완료되면 검수를 만든다. 기한은 행사 완료일(현지 날짜) + 검수 기간이다.</li>
 *   <li>플랫폼은 기한 안에 하자·지체를 기록하고 감액해 승인한다({@code ACTION_ORDER_INSPECTION}).
 *       기한이 지나면 감액 없이 승인으로 본다.</li>
 *   <li>승인하면 지급 대상 금액을 원장에 지급 의무(+)로 남긴다.</li>
 *   <li>감액하면 계약 기준표의 감액 유형을 고른다. 발주 금액의 30%를 넘는 감액은 PLATFORM_SUPER만
 *       승인한다({@code ACTION_INSPECTION_LARGE_DEDUCTION}, D13).</li>
 *   <li>파트너는 감액된 검수에 승인일부터 이의 기간 안에 이의를 제기할 수 있다. 이의 중에는 정산하지
 *       않고, PLATFORM_SUPER가 최종 감액을 정해 조정한다({@code ACTION_INSPECTION_DISPUTE_RESOLVE}).
 *       지급 대상 금액이 바뀐 만큼 원장에 조정을 남긴다.</li>
 * </ul>
 *
 * <p>검수 생성과 승인 간주는 {@link #syncInspections()}가 한다. 매일 배치, 검수 목록 조회, 정산 생성
 * 직전에 부르므로 배치가 돌기 전에도 화면은 최신이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderInspectionService {

    private final OrderInspectionRepository orderInspectionRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    private final PartnerSettlementPolicy partnerSettlementPolicy;
    private final PartnerPaymentControlPolicy partnerPaymentControlPolicy;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;
    private final OrderNotificationService orderNotificationService;

    /** 완료된 행사의 검수를 만들고, 기한이 지난 대기 검수를 승인 간주한다. 바꾼 건수를 돌려준다. */
    @Transactional
    public int syncInspections() {
        int changed = 0;
        for (PartnerOrder partnerOrder : partnerOrderRepository.findAcceptedCompletedWithoutInspection()) {
            LocalDateTime completedAt = partnerOrder.getCeremony().getCompletedAt();
            orderInspectionRepository.save(OrderInspection.builder()
                    .partnerOrder(partnerOrder)
                    .ceremonyCompletedAt(completedAt)
                    .dueDate(partnerSettlementPolicy.inspectionDueDate(toPlatformDate(completedAt)))
                    .build());
            changed++;
        }
        for (OrderInspection inspection : orderInspectionRepository.findPendingDueBefore(today())) {
            inspection.approveAsDeemed();
            partnerLedgerEntryRepository.save(PartnerLedgerEntry.payable(inspection));
            orderNotificationService.inspectionApproved(inspection);
            changed++;
        }
        return changed;
    }

    public Page<OrderInspectionDto.Response.InspectionSummary> findInspections(
            InspectionStatus status,
            Long organizationId,
            InspectionDisputeStatus disputeStatus,
            Pageable pageable
    ) {
        LocalDate today = today();
        return orderInspectionRepository.search(status, organizationId, disputeStatus, pageable)
                .map(inspection -> OrderInspectionDto.Response.InspectionSummary.from(
                        inspection, today, partnerPaymentControlPolicy.disputeDays()));
    }

    /** 감액 유형과 기준 비율(계약 기준표, D13). */
    public List<OrderInspectionDto.Response.DeductionTypeOption> findDeductionTypes() {
        return Arrays.stream(InspectionDeductionType.values())
                .map(OrderInspectionDto.Response.DeductionTypeOption::from)
                .toList();
    }

    /** 파트너 발주 상세에 붙일 검수(수행 평점 제외). 아직 행사가 완료되지 않았으면 null이다. */
    public OrderInspectionDto.Response.InspectionSummary findByPartnerOrder(Long partnerOrderId) {
        return orderInspectionRepository.findByPartnerOrderId(partnerOrderId)
                .map(inspection -> OrderInspectionDto.Response.InspectionSummary.forPartner(
                        inspection, today(), partnerPaymentControlPolicy.disputeDays()))
                .orElse(null);
    }

    @Transactional
    public OrderInspectionDto.Response.InspectionSummary approveInspection(
            Long inspectionId,
            Long adminUserId,
            String actingPlatformRole,
            OrderInspectionDto.Request.Approve request
    ) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_ORDER_INSPECTION")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        OrderInspection inspection = orderInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.INSPECTION_NOT_FOUND));
        if (!inspection.isPending()) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_NOT_PENDING);
        }
        LocalDate today = today();
        if (inspection.isOverdue(today)) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_DEADLINE_PASSED);
        }
        BigDecimal deduction = request.getDeductionAmount() == null ? BigDecimal.ZERO : request.getDeductionAmount();
        if (deduction.signum() < 0 || deduction.compareTo(inspection.getPartnerOrder().getTotalAmount()) > 0) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_DEDUCTION_INVALID);
        }
        String deductionReason = trimToNull(request.getDeductionReason());
        checkDeduction(inspection, deduction, deductionReason, request.getDeductionType(), actingPlatformRole);
        inspection.approve(
                adminUserId,
                today,
                trimToNull(request.getDefectNote()),
                trimToNull(request.getDelayNote()),
                deduction,
                deduction.signum() > 0 ? deductionReason : null,
                request.getDeductionType(),
                request.getPerformanceRating()
        );
        partnerLedgerEntryRepository.save(PartnerLedgerEntry.payable(inspection));

        orderNotificationService.inspectionApproved(inspection);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.APPROVE_ORDER_INSPECTION, null,
                inspection.getOrganization().getId(),
                "inspectionId=" + inspectionId + ", partnerOrderId=" + inspection.getPartnerOrder().getId()
                        + ", deductionAmount=" + deduction + ", deductionType=" + request.getDeductionType()
                        + ", payableAmount=" + inspection.getPayableAmount());
        return OrderInspectionDto.Response.InspectionSummary.from(inspection, today, partnerPaymentControlPolicy.disputeDays());
    }

    /**
     * 파트너의 감액 이의 제기. 발주가 파트너 조직의 것인지와 응답 권한은 호출하는 쪽이 확인한다.
     * 이의 중인 검수는 정산하지 않는다.
     */
    @Transactional
    public OrderInspectionDto.Response.InspectionSummary openDispute(Long partnerOrderId, Long userId, String reason) {
        OrderInspection inspection = orderInspectionRepository.findByPartnerOrderId(partnerOrderId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.INSPECTION_NOT_FOUND));
        int disputeDays = partnerPaymentControlPolicy.disputeDays();
        if (!inspection.canDispute(today(), disputeDays)) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_NOT_DISPUTABLE);
        }
        inspection.openDispute(userId, reason.trim());
        orderNotificationService.inspectionDisputed(inspection);
        return OrderInspectionDto.Response.InspectionSummary.forPartner(inspection, today(), disputeDays);
    }

    /** 이의 조정 — 최종 감액을 정하고, 지급 대상 금액이 바뀐 만큼 원장에 조정을 남긴다(PLATFORM_SUPER). */
    @Transactional
    public OrderInspectionDto.Response.InspectionSummary resolveDispute(
            Long inspectionId,
            Long adminUserId,
            String actingPlatformRole,
            OrderInspectionDto.Request.ResolveDispute request
    ) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_INSPECTION_DISPUTE_RESOLVE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        OrderInspection inspection = orderInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.INSPECTION_NOT_FOUND));
        if (!inspection.isDisputeOpen()) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_DISPUTE_NOT_OPEN);
        }
        BigDecimal deduction = request.getDeductionAmount();
        if (deduction.compareTo(inspection.getPartnerOrder().getTotalAmount()) > 0) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_DEDUCTION_INVALID);
        }
        String deductionReason = trimToNull(request.getDeductionReason());
        checkDeduction(inspection, deduction, deductionReason, request.getDeductionType(), actingPlatformRole);
        boolean hasDeduction = deduction.signum() > 0;
        LocalDate today = today();
        BigDecimal difference = inspection.resolveDispute(
                adminUserId,
                deduction,
                hasDeduction ? deductionReason : null,
                hasDeduction ? request.getDeductionType() : null,
                request.getResolutionNote().trim()
        );
        if (difference.signum() != 0) {
            partnerLedgerEntryRepository.save(PartnerLedgerEntry.adjustment(inspection, difference, today));
        }

        orderNotificationService.inspectionDisputeResolved(inspection);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.RESOLVE_INSPECTION_DISPUTE, null,
                inspection.getOrganization().getId(),
                "inspectionId=" + inspectionId + ", deductionAmount=" + deduction + ", payableDifference=" + difference);
        return OrderInspectionDto.Response.InspectionSummary.from(inspection, today, partnerPaymentControlPolicy.disputeDays());
    }

    /** 감액이 있으면 사유·유형이 필요하고, 큰 감액은 PLATFORM_SUPER만 정할 수 있다(D13). */
    private void checkDeduction(
            OrderInspection inspection,
            BigDecimal deduction,
            String deductionReason,
            InspectionDeductionType deductionType,
            String actingPlatformRole
    ) {
        if (deduction.signum() <= 0) {
            return;
        }
        if (deductionReason == null) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_DEDUCTION_REASON_REQUIRED);
        }
        if (deductionType == null) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_DEDUCTION_TYPE_REQUIRED);
        }
        if (partnerPaymentControlPolicy.isLargeDeduction(inspection.getPartnerOrder().getTotalAmount(), deduction)
                && !rolePermissionService.isAllowed(actingPlatformRole, "ACTION_INSPECTION_LARGE_DEDUCTION")) {
            throw new ApplicationException(OrderErrorCode.INSPECTION_LARGE_DEDUCTION_FORBIDDEN);
        }
    }

    private static LocalDate today() {
        return LocalDate.now(SalesOrderService.PLATFORM_ZONE);
    }

    /** 엔티티 시각은 JVM 기본 타임존(UTC) 기준이라 플랫폼 현지 날짜로 바꾼다. */
    private static LocalDate toPlatformDate(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(SalesOrderService.PLATFORM_ZONE).toLocalDate();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
