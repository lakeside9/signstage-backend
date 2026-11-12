package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.PartnerOffsetDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffset;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffsetStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOffsetRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파트너 귀책 상계 — signstage-docs business/platform-order-partner-subcontract-review.md D13(2026-11-12 결정).
 * 고객 환불 중 파트너 귀책분처럼 파트너에게 돌려받을 금액을 수락된 발주에 묶어 등록하면, 다음 월 정산에서
 * 지급액에서 뺀다({@link PartnerPayoutService}). 등록하면 원장에 상계(−)를, 취소하면 상계 취소(+)를
 * 남긴다. 정산에 반영되기 전(대기)에만 취소할 수 있다. 권한은 지급 처리와 같다
 * ({@code ACTION_PARTNER_PAYMENT_APPROVE}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerOffsetService {

    private final PartnerOffsetRepository partnerOffsetRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public Page<PartnerOffsetDto.Response.OffsetSummary> findOffsets(
            PartnerOffsetStatus status,
            Long organizationId,
            Pageable pageable
    ) {
        return partnerOffsetRepository.search(status, organizationId, pageable)
                .map(PartnerOffsetDto.Response.OffsetSummary::from);
    }

    @Transactional
    public PartnerOffsetDto.Response.OffsetSummary registerOffset(
            Long adminUserId,
            String actingPlatformRole,
            PartnerOffsetDto.Request.Register request
    ) {
        checkCanManage(actingPlatformRole);
        PartnerOrder partnerOrder = partnerOrderRepository.findById(request.getPartnerOrderId())
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PARTNER_ORDER_NOT_FOUND));
        if (partnerOrder.getStatus() != PartnerOrderStatus.ACCEPTED) {
            throw new ApplicationException(OrderErrorCode.OFFSET_PARTNER_ORDER_INVALID);
        }
        if (request.getAmount().signum() <= 0 || request.getAmount().compareTo(partnerOrder.getTotalAmount()) > 0) {
            throw new ApplicationException(OrderErrorCode.OFFSET_AMOUNT_INVALID);
        }
        LocalDate today = LocalDate.now(SalesOrderService.PLATFORM_ZONE);
        LocalDate occurredOn = request.getOccurredOn() != null ? request.getOccurredOn() : today;
        if (occurredOn.isAfter(today)) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        PartnerOffset offset = partnerOffsetRepository.save(PartnerOffset.builder()
                .partnerOrder(partnerOrder)
                .amount(request.getAmount())
                .reason(request.getReason().trim())
                .occurredOn(occurredOn)
                .build());
        partnerLedgerEntryRepository.save(PartnerLedgerEntry.offset(offset));

        auditLogRecorder.record(adminUserId, PlatformAdminAction.REGISTER_PARTNER_OFFSET, null,
                offset.getOrganization().getId(),
                "partnerOffsetId=" + offset.getId() + ", partnerOrderId=" + partnerOrder.getId()
                        + ", amount=" + offset.getAmount());
        return PartnerOffsetDto.Response.OffsetSummary.from(offset);
    }

    @Transactional
    public PartnerOffsetDto.Response.OffsetSummary cancelOffset(
            Long partnerOffsetId,
            Long adminUserId,
            String actingPlatformRole,
            String reason
    ) {
        checkCanManage(actingPlatformRole);
        PartnerOffset offset = partnerOffsetRepository.findById(partnerOffsetId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.OFFSET_NOT_FOUND));
        if (!offset.isPending() || offset.isCarryOver()) {
            throw new ApplicationException(OrderErrorCode.OFFSET_NOT_CANCELLABLE);
        }
        offset.cancel(reason.trim());
        partnerLedgerEntryRepository.save(PartnerLedgerEntry.offsetCancelled(
                offset, LocalDate.now(SalesOrderService.PLATFORM_ZONE)));

        auditLogRecorder.record(adminUserId, PlatformAdminAction.CANCEL_PARTNER_OFFSET, null,
                offset.getOrganization().getId(), "partnerOffsetId=" + partnerOffsetId + ", reason=" + reason);
        return PartnerOffsetDto.Response.OffsetSummary.from(offset);
    }

    private void checkCanManage(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_PARTNER_PAYMENT_APPROVE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
