package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.model.PlatformOrderCeremonyCommand;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.PartnerOrderDto;
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
 * (D03 안 A). 응답 기한이 지났으면 수락·거절을 받지 않는다.
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

        List<PlatformOrderCeremonyCommand.ScopeOption> options = salesOrderService.findOptionLines(salesOrder.getId()).stream()
                .map(line -> new PlatformOrderCeremonyCommand.ScopeOption(line.getUnitProduct().getId(), line.getQuantity()))
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
                options
        ));
        partnerOrder.accept(currentUserId, ceremony);
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
                salesOrderService.findOptionLines(partnerOrder.getSalesOrder().getId()),
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
