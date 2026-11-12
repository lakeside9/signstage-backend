package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.model.PlatformOrderCeremonyCommand;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.PartnerOrderDto;
import com.eformworks.signstage.backend.feature.order.dto.SalesOrderChangeDto;
import com.eformworks.signstage.backend.feature.order.entity.Customer;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파트너의 발주 조회·수락·거절 — signstage-docs business/platform-order-partner-subcontract-review.md
 * 3장 5단계, D04. 조회와 응답 모두 {@code ACTION_PARTNER_ORDER_RESPOND}(OWNER·ADMIN)가 필요하다 —
 * 발주 금액이 들어 있어 운영 담당자(OPERATOR)에게는 보이지 않는다.
 *
 * <p>수락하면 {@link PlatformOrderCeremonyService}로 플랫폼 수주 행사를 만들어 발주에 연결한다
 * (D03 안 A). 수락 후 취소된 이전 발주의 행사가 있으면 새로 만들지 않고 넘겨받는다(파트너 교체).
 * 응답 기한이 지났으면 수락·거절을 받지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerOrderResponseService {

    private final PartnerOrderRepository partnerOrderRepository;
    private final MemberRepository memberRepository;
    private final RolePermissionService rolePermissionService;
    private final SalesOrderService salesOrderService;
    private final PlatformOrderCeremonyService platformOrderCeremonyService;
    private final OrderInspectionService orderInspectionService;
    private final SalesOrderChangeService salesOrderChangeService;
    private final OrderNotificationService orderNotificationService;

    public Page<PartnerOrderDto.Response.PartnerOrderSummary> findReceivedOrders(
            Long organizationId,
            Long currentUserId,
            PartnerOrderStatus status,
            Pageable pageable
    ) {
        checkCanRespond(organizationId, currentUserId);
        LocalDateTime now = LocalDateTime.now(SalesOrderService.PLATFORM_ZONE);
        return partnerOrderRepository.search(status, organizationId, pageable)
                .map(order -> PartnerOrderDto.Response.PartnerOrderSummary.from(order, now));
    }

    public PartnerOrderDto.Response.PartnerOrderDetail retrieveReceivedOrder(
            Long organizationId,
            Long partnerOrderId,
            Long currentUserId
    ) {
        checkCanRespond(organizationId, currentUserId);
        PartnerOrder partnerOrder = findInOrganizationOrThrow(organizationId, partnerOrderId);
        return toDetail(partnerOrder);
    }

    @Transactional
    public PartnerOrderDto.Response.PartnerOrderDetail acceptOrder(
            Long organizationId,
            Long partnerOrderId,
            Long currentUserId
    ) {
        checkCanRespond(organizationId, currentUserId);
        PartnerOrder partnerOrder = findRespondableOrThrow(organizationId, partnerOrderId);
        SalesOrder salesOrder = partnerOrder.getSalesOrder();
        Customer customer = salesOrder.getCustomer();

        Ceremony carriedCeremony = findCarriedCeremony(salesOrder.getId());
        if (carriedCeremony != null) {
            partnerOrder.accept(currentUserId, platformOrderCeremonyService.transferPlatformOrderCeremony(
                    carriedCeremony, organizationId, currentUserId
            ));
            orderNotificationService.partnerOrderResponded(partnerOrder);
            return toDetail(partnerOrder);
        }

        List<PlatformOrderCeremonyCommand.ScopeOption> options = salesOrderService.findOptionLines(salesOrder.getId()).stream()
                .map(line -> new PlatformOrderCeremonyCommand.ScopeOption(
                        line.getUnitProduct().getId(), line.getQuantity(), line.getTaxCode()
                ))
                .toList();
        Ceremony ceremony = platformOrderCeremonyService.createPlatformOrderCeremony(new PlatformOrderCeremonyCommand(
                organizationId,
                currentUserId,
                salesOrder.getBillingPlan().getId(),
                salesOrder.getTitle(),
                salesOrder.getLocation(),
                salesOrder.getStartsAt(),
                salesOrder.getEndsAt(),
                customer.getName(),
                customer.getContactName(),
                customer.getContactPhone(),
                customer.getContactEmail(),
                options,
                salesOrder.getCurrencyCode(),
                salesOrderService.findPlanTaxCode(salesOrder.getId())
        ));
        partnerOrder.accept(currentUserId, ceremony);
        orderNotificationService.partnerOrderResponded(partnerOrder);
        return toDetail(partnerOrder);
    }

    @Transactional
    public PartnerOrderDto.Response.PartnerOrderDetail rejectOrder(
            Long organizationId,
            Long partnerOrderId,
            Long currentUserId,
            String reason
    ) {
        checkCanRespond(organizationId, currentUserId);
        PartnerOrder partnerOrder = findRespondableOrThrow(organizationId, partnerOrderId);
        partnerOrder.reject(currentUserId, reason);
        orderNotificationService.partnerOrderResponded(partnerOrder);
        return toDetail(partnerOrder);
    }

    /** 감액된 검수에 이의를 제기한다(D13). 발주 응답 권한(OWNER·ADMIN)이 필요하다. */
    @Transactional
    public PartnerOrderDto.Response.PartnerOrderDetail disputeInspection(
            Long organizationId,
            Long partnerOrderId,
            Long currentUserId,
            String reason
    ) {
        checkCanRespond(organizationId, currentUserId);
        PartnerOrder partnerOrder = findInOrganizationOrThrow(organizationId, partnerOrderId);
        orderInspectionService.openDispute(partnerOrder.getId(), currentUserId, reason);
        return toDetail(partnerOrder);
    }

    /**
     * 수락 후 취소된 이전 발주의 행사 — 파트너 교체(D03). 수주 하나에 수락된 발주는 하나뿐이므로
     * 행사가 연결된 취소 발주가 있으면 그 행사가 이 수주의 행사다.
     */
    /** 받은 발주의 범위 변경과 범위 변경 요청(D08). */
    public SalesOrderChangeDto.Response.PartnerOverview findScopeChanges(
            Long organizationId,
            Long partnerOrderId,
            Long currentUserId
    ) {
        checkCanRespond(organizationId, currentUserId);
        return salesOrderChangeService.findPartnerChanges(findInOrganizationOrThrow(organizationId, partnerOrderId));
    }

    /** 플랫폼이 제안한 범위 변경에 동의한다. 고객 수락도 받았으면 바로 반영된다(D08). */
    @Transactional
    public SalesOrderChangeDto.Response.PartnerOverview acceptScopeChange(
            Long organizationId,
            Long partnerOrderId,
            Long changeId,
            Long currentUserId
    ) {
        checkCanRespond(organizationId, currentUserId);
        PartnerOrder partnerOrder = findInOrganizationOrThrow(organizationId, partnerOrderId);
        salesOrderChangeService.acceptByPartner(partnerOrder, changeId, currentUserId);
        return salesOrderChangeService.findPartnerChanges(partnerOrder);
    }

    @Transactional
    public SalesOrderChangeDto.Response.PartnerOverview rejectScopeChange(
            Long organizationId,
            Long partnerOrderId,
            Long changeId,
            Long currentUserId,
            String reason
    ) {
        checkCanRespond(organizationId, currentUserId);
        PartnerOrder partnerOrder = findInOrganizationOrThrow(organizationId, partnerOrderId);
        salesOrderChangeService.rejectByPartner(partnerOrder, changeId, currentUserId, reason);
        return salesOrderChangeService.findPartnerChanges(partnerOrder);
    }

    /** 파트너가 범위 변경 필요를 알린다 — 범위는 플랫폼이 고객과 정해 바꾼다(D08). */
    @Transactional
    public SalesOrderChangeDto.Response.PartnerOverview submitScopeRequest(
            Long organizationId,
            Long partnerOrderId,
            Long currentUserId,
            String content
    ) {
        checkCanRespond(organizationId, currentUserId);
        PartnerOrder partnerOrder = findInOrganizationOrThrow(organizationId, partnerOrderId);
        salesOrderChangeService.submitScopeRequest(partnerOrder, currentUserId, content);
        return salesOrderChangeService.findPartnerChanges(partnerOrder);
    }

    private Ceremony findCarriedCeremony(Long salesOrderId) {
        return partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(salesOrderId).stream()
                .filter(order -> order.getStatus() == PartnerOrderStatus.CANCELLED && order.getCeremony() != null)
                .map(PartnerOrder::getCeremony)
                .findFirst()
                .orElse(null);
    }

    private PartnerOrder findRespondableOrThrow(Long organizationId, Long partnerOrderId) {
        PartnerOrder partnerOrder = findInOrganizationOrThrow(organizationId, partnerOrderId);
        if (partnerOrder.getStatus() != PartnerOrderStatus.ISSUED) {
            throw new ApplicationException(OrderErrorCode.PARTNER_ORDER_NOT_ISSUED);
        }
        if (partnerOrder.isOverdue(LocalDateTime.now(SalesOrderService.PLATFORM_ZONE))) {
            throw new ApplicationException(OrderErrorCode.PARTNER_ORDER_EXPIRED);
        }
        return partnerOrder;
    }

    private PartnerOrder findInOrganizationOrThrow(Long organizationId, Long partnerOrderId) {
        return partnerOrderRepository.findByIdAndOrganizationId(partnerOrderId, organizationId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.PARTNER_ORDER_NOT_FOUND));
    }

    private PartnerOrderDto.Response.PartnerOrderDetail toDetail(PartnerOrder partnerOrder) {
        return PartnerOrderDto.Response.PartnerOrderDetail.of(
                partnerOrder,
                salesOrderService.findPartnerScopeLines(partnerOrder.getSalesOrder().getId()),
                orderInspectionService.findByPartnerOrder(partnerOrder.getId()),
                LocalDateTime.now(SalesOrderService.PLATFORM_ZONE)
        );
    }

    private void checkCanRespond(Long organizationId, Long currentUserId) {
        Member member = memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, currentUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        if (!rolePermissionService.isAllowed(member.getRole().name(), "ACTION_PARTNER_ORDER_RESPOND")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
