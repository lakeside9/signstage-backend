package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminPartnerOrderDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderAmount;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationStatus;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.service.PartnerSubcontractProfileService;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼의 파트너 발주 발급·취소 — signstage-docs
 * business/platform-order-partner-subcontract-review.md 3장 4단계, D04·D05·D22·D28.
 *
 * <ul>
 *   <li>고객이 수락한(ACCEPTED) 수주에만 발급한다. 수주 하나에 응답 대기·수락 발주는 하나뿐이다
 *       (분할 발주 없음). 기한이 지난 응답 대기 발주는 새 발주를 낼 때 EXPIRED로 확정한다.</li>
 *   <li>파트너는 활성 조직이고 데모 조직이 아니며 외주 정보 요건(D22)을 채워야 한다.</li>
 *   <li>발주 금액은 파트너 단가표로 다시 계산하고(조정은 사유 필수), 최소 마진을 다시 검사한다.
 *       금액은 발주에 스냅샷한다.</li>
 *   <li>응답 대기 발주는 발급 권한으로 취소한다. 수락한 발주는 행사 완료 전까지만, 파트너 교체
 *       권한({@code ACTION_PARTNER_ORDER_REPLACE}, SUPER)으로 사유를 남기고 취소한다(D03). 행사는
 *       남겨 두고 이전 파트너 담당자 배정만 지운다 — 다음에 발급한 발주를 수락하는 파트너가 행사를
 *       넘겨받는다({@link PartnerOrderResponseService#acceptOrder}). 하도급법상 부당 취소가 되지 않도록
 *       사유와 감사 로그를 남긴다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerOrderService {

    private final PartnerOrderRepository partnerOrderRepository;
    private final SalesOrderService salesOrderService;
    private final OrganizationRepository organizationRepository;
    private final PartnerSubcontractProfileService partnerSubcontractProfileService;
    private final OrderPricingService orderPricingService;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;
    private final PlatformOrderCeremonyService platformOrderCeremonyService;
    private final SalesOrderChangeService salesOrderChangeService;
    private final OrderNotificationService orderNotificationService;

    public Page<PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary> findPartnerOrders(
            PartnerOrderStatus status,
            Long organizationId,
            Pageable pageable
    ) {
        LocalDateTime now = LocalDateTime.now(SalesOrderService.PLATFORM_ZONE);
        return partnerOrderRepository.search(status, organizationId, pageable)
                .map(order -> PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary.from(order, now));
    }

    /** 발급 전 금액·마진·파트너 요건을 미리 본다. 저장하지 않는다. */
    public PlatformAdminPartnerOrderDto.Response.PartnerOrderPreview previewPartnerOrder(
            Long salesOrderId,
            PlatformAdminPartnerOrderDto.Request.PreviewPartnerOrder request
    ) {
        SalesOrder salesOrder = salesOrderService.findSalesOrderOrThrow(salesOrderId);
        Organization partner = findOrganizationOrThrow(request.getOrganizationId());
        PartnerOrderAmount amount = calculateAmount(salesOrder, partner.getId(), request.getAdjustmentAmount());
        boolean marginSufficient = orderPricingService.isPartnerOrderMarginSufficient(
                salesOrder.getSupplyAmount(), amount.totalAmount()
        );
        return PlatformAdminPartnerOrderDto.Response.PartnerOrderPreview.of(
                partner.getId(), isEligible(partner), amount, salesOrder.getSupplyAmount(), marginSufficient
        );
    }

    @Transactional
    public PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary issuePartnerOrder(
            Long salesOrderId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminPartnerOrderDto.Request.IssuePartnerOrder request
    ) {
        checkCanIssue(actingPlatformRole);
        SalesOrder salesOrder = salesOrderService.findSalesOrderOrThrow(salesOrderId);
        if (salesOrder.getStatus() != SalesOrderStatus.ACCEPTED) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_ACCEPTED);
        }
        if (salesOrderChangeService.hasProposedChange(salesOrderId)) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_IN_PROGRESS);
        }
        LocalDateTime now = LocalDateTime.now(SalesOrderService.PLATFORM_ZONE);
        if (!request.getResponseDueAt().isAfter(now)) {
            throw new ApplicationException(OrderErrorCode.PARTNER_ORDER_DUE_INVALID);
        }
        BigDecimal adjustment = request.getAdjustmentAmount() == null ? BigDecimal.ZERO : request.getAdjustmentAmount();
        if (adjustment.signum() != 0 && (request.getAdjustmentReason() == null || request.getAdjustmentReason().isBlank())) {
            throw new ApplicationException(OrderErrorCode.PARTNER_ADJUSTMENT_REASON_REQUIRED);
        }
        List<PartnerOrder> existingOrders = partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(salesOrderId);
        if (existingOrders.stream().anyMatch(order -> order.isActive(now))) {
            throw new ApplicationException(OrderErrorCode.PARTNER_ORDER_ALREADY_ACTIVE);
        }
        existingOrders.stream().filter(order -> order.isOverdue(now)).forEach(PartnerOrder::expire);

        Organization partner = findOrganizationOrThrow(request.getOrganizationId());
        if (!isEligible(partner)) {
            throw new ApplicationException(OrderErrorCode.PARTNER_NOT_ELIGIBLE);
        }
        PartnerOrderAmount amount = calculateAmount(salesOrder, partner.getId(), adjustment);
        orderPricingService.checkPartnerOrderMargin(salesOrder.getSupplyAmount(), amount.totalAmount());

        PartnerOrder partnerOrder = partnerOrderRepository.save(PartnerOrder.builder()
                .salesOrder(salesOrder)
                .organization(partner)
                .currencyCode(salesOrder.getCurrencyCode())
                .baseFee(amount.baseFee())
                .tabletCount(amount.tabletCount())
                .tabletUnitRate(amount.tabletUnitRate())
                .onsiteStaffPersonDays(amount.onsiteStaffPersonDays())
                .onsiteStaffDayRate(amount.onsiteStaffDayRate())
                .regionSurcharge(amount.regionSurcharge())
                .extras(salesOrder.scopeExtras())
                .lodgingNightRate(amount.lodgingNightRate())
                .adjustmentAmount(amount.adjustmentAmount())
                .adjustmentReason(adjustment.signum() != 0 ? request.getAdjustmentReason().trim() : null)
                .totalAmount(amount.totalAmount())
                .responseDueAt(request.getResponseDueAt())
                .issuedBy(adminUserId)
                .partnerRateCardId(amount.partnerRateCardId())
                .regionSurchargeId(amount.regionSurchargeId())
                .build());

        orderNotificationService.partnerOrderIssued(partnerOrder);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.ISSUE_PARTNER_ORDER, null, partner.getId(),
                "partnerOrderId=" + partnerOrder.getId() + ", salesOrderId=" + salesOrderId
                        + ", totalAmount=" + partnerOrder.getTotalAmount());
        return PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary.from(partnerOrder, now);
    }

    @Transactional
    public PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary cancelPartnerOrder(
            Long partnerOrderId,
            Long adminUserId,
            String actingPlatformRole,
            String reason
    ) {
        checkCanIssue(actingPlatformRole);
        PartnerOrder partnerOrder = partnerOrderRepository.findById(partnerOrderId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PARTNER_ORDER_NOT_FOUND));
        boolean afterAcceptance = partnerOrder.getStatus() == PartnerOrderStatus.ACCEPTED;
        if (afterAcceptance) {
            if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_PARTNER_ORDER_REPLACE")) {
                throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
            }
            if (!partnerOrder.isCancellableAfterAcceptance()) {
                throw new ApplicationException(OrderErrorCode.PARTNER_ORDER_CEREMONY_COMPLETED);
            }
        } else if (partnerOrder.getStatus() != PartnerOrderStatus.ISSUED) {
            throw new ApplicationException(OrderErrorCode.PARTNER_ORDER_NOT_CANCELLABLE);
        }
        partnerOrder.cancel(reason);
        if (afterAcceptance) {
            platformOrderCeremonyService.suspendPartnerAccess(partnerOrder.getCeremony());
            salesOrderChangeService.cancelProposedForPartnerOrder(partnerOrderId, "발주 취소(파트너 교체)");
        }

        orderNotificationService.partnerOrderCancelled(partnerOrder, reason);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CANCEL_PARTNER_ORDER, null,
                partnerOrder.getOrganization().getId(), "partnerOrderId=" + partnerOrderId
                        + ", afterAcceptance=" + afterAcceptance + ", reason=" + reason);
        return PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary.from(
                partnerOrder, LocalDateTime.now(SalesOrderService.PLATFORM_ZONE)
        );
    }

    private PartnerOrderAmount calculateAmount(SalesOrder salesOrder, Long organizationId, BigDecimal adjustmentAmount) {
        return orderPricingService.calculatePartnerOrderAmount(
                organizationId, salesOrder.getTabletCount(), salesOrder.getOnsiteStaffPersonDays(), salesOrder.scopeExtras(),
                salesOrder.getRegion(),
                adjustmentAmount, salesOrder.currencyPolicy(), LocalDate.now(SalesOrderService.PLATFORM_ZONE)
        );
    }

    private boolean isEligible(Organization partner) {
        return partner.getStatus() == OrganizationStatus.ACTIVE
                && !partner.isDemo()
                && partnerSubcontractProfileService.isOrderEligible(partner.getId());
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private void checkCanIssue(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_PARTNER_ORDER_ISSUE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
