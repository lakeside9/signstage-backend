package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.ceremony.model.PlatformOrderCeremonyCommand;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminSalesOrderDto;
import com.eformworks.signstage.backend.feature.order.dto.SalesOrderChangeDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceType;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChange;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeLine;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLineType;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.ScopeChangeRequest;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCalculation;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteOption;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderChangeLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderChangeRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.ScopeChangeRequestRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수락된 수주의 범위 변경 — signstage-docs business/platform-order-partner-subcontract-review.md D08.
 *
 * <ul>
 *   <li>플랫폼이 고객 변경 계약과 발주 변경을 한 건으로 제안한다({@code ACTION_SALES_ORDER_MANAGE}).
 *       플랜·권역은 그대로 두고 옵션 수량·영업 할인·장소·일정을 바꾼다. 고객 금액은 오늘 가격표로,
 *       발주 금액은 발주의 단가 스냅샷으로 다시 계산하고 최소 마진을 검사한다.</li>
 *   <li>수주에 진행 중인 변경은 하나뿐이다. 응답 대기 발주가 있거나, 행사가 완료됐거나, 잔금을 청구했으면
 *       제안할 수 없다. 변경 후 공급가가 이미 청구한 공급가보다 작아질 수도 없다.</li>
 *   <li>고객 수락(플랫폼이 기록)과 파트너 동의(제안 때 수락된 발주가 있을 때)를 모두 받으면 수주 줄·금액,
 *       발주 수량·금액, 행사 장소·일정과 늘어난 옵션을 반영한다. 파트너가 거절하면 반영하지 않는다.</li>
 *   <li>파트너는 범위를 직접 바꾸지 않고 "범위 변경 요청"으로 필요를 알린다. 플랫폼이 변경을 제안하면
 *       그 수주의 열린 요청을 함께 닫는다. 고객 수락 없이 늘린 범위는 발주 금액에 반영하지 않는다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderChangeService {

    private final SalesOrderChangeRepository salesOrderChangeRepository;
    private final SalesOrderChangeLineRepository salesOrderChangeLineRepository;
    private final ScopeChangeRequestRepository scopeChangeRequestRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final SalesOrderService salesOrderService;
    private final OrderPricingService orderPricingService;
    private final PlatformOrderCeremonyService platformOrderCeremonyService;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;
    private final OrderNotificationService orderNotificationService;

    public SalesOrderChangeDto.Response.AdminOverview findChanges(Long salesOrderId) {
        salesOrderService.findSalesOrderOrThrow(salesOrderId);
        List<SalesOrderChange> changes = salesOrderChangeRepository.findAllBySalesOrderIdOrderByIdDesc(salesOrderId);
        Map<Long, List<SalesOrderChangeLine>> lines = linesByChange(changes);
        return new SalesOrderChangeDto.Response.AdminOverview(
                changes.stream()
                        .map(change -> SalesOrderChangeDto.Response.AdminChange.of(change, lines.getOrDefault(change.getId(), List.of())))
                        .toList(),
                scopeChangeRequestRepository.findAllByPartnerOrderSalesOrderIdOrderByIdDesc(salesOrderId).stream()
                        .map(SalesOrderChangeDto.Response.ScopeRequestSummary::from)
                        .toList()
        );
    }

    @Transactional
    public SalesOrderChangeDto.Response.AdminChange proposeChange(
            Long salesOrderId,
            Long adminUserId,
            String actingPlatformRole,
            SalesOrderChangeDto.Request.Propose request
    ) {
        checkCanManage(actingPlatformRole);
        SalesOrder salesOrder = salesOrderService.findSalesOrderOrThrow(salesOrderId);
        if (salesOrder.getStatus() != SalesOrderStatus.ACCEPTED) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_ACCEPTED);
        }
        if (salesOrderChangeRepository.existsBySalesOrderIdAndStatus(salesOrderId, SalesOrderChangeStatus.PROPOSED)) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_IN_PROGRESS);
        }
        if (request.getStartsAt() != null && request.getEndsAt() != null && request.getEndsAt().isBefore(request.getStartsAt())) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        LocalDateTime now = LocalDateTime.now(SalesOrderService.PLATFORM_ZONE);
        List<PartnerOrder> partnerOrders = partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(salesOrderId);
        if (partnerOrders.stream().anyMatch(order -> order.effectiveStatus(now) == PartnerOrderStatus.ISSUED)) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_PARTNER_ORDER_PENDING);
        }
        PartnerOrder acceptedOrder = partnerOrders.stream()
                .filter(order -> order.getStatus() == PartnerOrderStatus.ACCEPTED)
                .findFirst()
                .orElse(null);
        if (acceptedOrder != null && !acceptedOrder.isCancellableAfterAcceptance()) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_CEREMONY_COMPLETED);
        }
        List<CustomerInvoice> activeInvoices = customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(salesOrderId).stream()
                .filter(CustomerInvoice::isActive)
                .toList();
        if (activeInvoices.stream().anyMatch(invoice -> invoice.getInvoiceType() == CustomerInvoiceType.BALANCE)) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_BALANCE_INVOICED);
        }

        LocalDate today = LocalDate.now(SalesOrderService.PLATFORM_ZONE);
        List<SalesQuoteOption> options = request.getOptions() == null
                ? List.of()
                : request.getOptions().stream()
                        .map(option -> new SalesQuoteOption(option.getUnitProductId(), option.getQuantity()))
                        .toList();
        SalesQuoteCalculation calculation = orderPricingService.calculateSalesQuote(
                salesOrder.getBillingPlan(), options, PlatformAdminSalesOrderDto.Request.CustomItem.toModels(request.getCustomItems()), salesOrder.getRegion(), request.scopeExtrasOr(salesOrder.scopeExtras()),
                request.quoteAdjustment(), salesOrder.currencyPolicy(), today
        );
        BigDecimal invoicedSupply = activeInvoices.stream()
                .map(CustomerInvoice::getSupplyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (calculation.supplyAmount().compareTo(invoicedSupply) < 0) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_BELOW_INVOICED);
        }
        BigDecimal partnerAmount = null;
        BigDecimal estimatedPartnerAmount;
        if (acceptedOrder != null) {
            partnerAmount = acceptedOrder.amountFor(
                    calculation.tabletCount(), calculation.wifiCount(), calculation.onsiteStaffPersonDays(), calculation.extras(),
                    calculation.customItemPartnerAmount()
            );
            orderPricingService.checkPartnerOrderMargin(calculation.supplyAmount(), partnerAmount);
            estimatedPartnerAmount = partnerAmount;
        } else {
            estimatedPartnerAmount = orderPricingService.estimatePartnerAmountWithinMargin(
                    calculation, salesOrder.getRegion(), salesOrder.currencyPolicy(), today
            );
        }

        SalesOrderChange change = salesOrderChangeRepository.save(SalesOrderChange.builder()
                .salesOrder(salesOrder)
                .partnerOrder(acceptedOrder)
                .reason(request.getReason().trim())
                .tabletCount(calculation.tabletCount())
                .wifiCount(calculation.wifiCount())
                .onsiteStaffPersonDays(calculation.onsiteStaffPersonDays())
                .extras(calculation.extras())
                .customItemPartnerAmount(calculation.customItemPartnerAmount())
                .supplyAmount(calculation.supplyAmount())
                .vatAmount(calculation.vatAmount())
                .discountReason(request.getDiscountReason() == null || request.getDiscountReason().isBlank()
                        ? null
                        : request.getDiscountReason().trim())
                .estimatedPartnerAmount(estimatedPartnerAmount)
                .discountRatePercent(request.quoteAdjustment().discountRatePercent())
                .truncationUnit(request.quoteAdjustment().truncationUnit())
                .partnerAmount(partnerAmount)
                // 장소·일정을 비워 보내면 지금 값을 그대로 둔다.
                .location(request.getLocation() == null || request.getLocation().isBlank()
                        ? salesOrder.getLocation()
                        : request.getLocation().trim())
                .startsAt(request.getStartsAt() != null ? request.getStartsAt() : salesOrder.getStartsAt())
                .endsAt(request.getEndsAt() != null ? request.getEndsAt() : salesOrder.getEndsAt())
                .proposedBy(adminUserId)
                .regionSurchargeId(calculation.regionSurchargeId())
                .build());
        int displayOrder = 0;
        List<SalesOrderChangeLine> lines = new ArrayList<>();
        for (SalesQuoteCalculation.Line line : calculation.lines()) {
            lines.add(salesOrderChangeLineRepository.save(SalesOrderChangeLine.builder()
                    .salesOrderChange(change)
                    .lineType(line.lineType())
                    .unitProduct(line.unitProduct())
                    .name(line.name())
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .amount(line.amount())
                    .taxCode(line.taxCode())
                    .taxRatePercent(line.taxRatePercent())
                    .taxAmount(line.taxAmount())
                    .displayOrder(displayOrder++)
                    .customerPriceId(line.customerPriceId())
                    .partnerUnitPrice(line.partnerUnitPrice())
                    .build()));
        }
        scopeChangeRequestRepository.findAllByPartnerOrderSalesOrderIdAndClosedAtIsNull(salesOrderId)
                .forEach(scopeRequest -> scopeRequest.close(adminUserId, "범위 변경 제안으로 처리", change));

        orderNotificationService.scopeChangeProposed(change);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.PROPOSE_SALES_ORDER_CHANGE, null,
                acceptedOrder != null ? acceptedOrder.getOrganization().getId() : null,
                "salesOrderId=" + salesOrderId + ", changeId=" + change.getId()
                        + ", supplyAmount=" + change.getBeforeSupplyAmount() + "->" + change.getSupplyAmount()
                        + ", partnerAmount=" + change.getBeforePartnerAmount() + "->" + change.getPartnerAmount());
        return SalesOrderChangeDto.Response.AdminChange.of(change, lines);
    }

    @Transactional
    public SalesOrderChangeDto.Response.AdminChange recordCustomerAcceptance(
            Long salesOrderId,
            Long changeId,
            Long adminUserId,
            String actingPlatformRole,
            String acceptedByName
    ) {
        checkCanManage(actingPlatformRole);
        SalesOrderChange change = findProposedOrThrow(salesOrderId, changeId);
        if (change.isCustomerAccepted()) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_CUSTOMER_ALREADY_ACCEPTED);
        }
        change.recordCustomerAcceptance(acceptedByName.trim());
        applyIfReady(change);

        auditLogRecorder.record(adminUserId, PlatformAdminAction.RECORD_SALES_ORDER_CHANGE_ACCEPTANCE, null, null,
                "salesOrderId=" + salesOrderId + ", changeId=" + changeId + ", acceptedByName=" + acceptedByName
                        + ", applied=" + (change.getStatus() == SalesOrderChangeStatus.APPLIED));
        return toAdminChange(change);
    }

    @Transactional
    public SalesOrderChangeDto.Response.AdminChange cancelChange(
            Long salesOrderId,
            Long changeId,
            Long adminUserId,
            String actingPlatformRole,
            String reason
    ) {
        checkCanManage(actingPlatformRole);
        SalesOrderChange change = findProposedOrThrow(salesOrderId, changeId);
        change.cancel(reason.trim());

        auditLogRecorder.record(adminUserId, PlatformAdminAction.CANCEL_SALES_ORDER_CHANGE, null, null,
                "salesOrderId=" + salesOrderId + ", changeId=" + changeId + ", reason=" + reason);
        return toAdminChange(change);
    }

    @Transactional
    public SalesOrderChangeDto.Response.ScopeRequestSummary closeScopeRequest(
            Long salesOrderId,
            Long requestId,
            Long adminUserId,
            String actingPlatformRole,
            String note
    ) {
        checkCanManage(actingPlatformRole);
        ScopeChangeRequest scopeRequest = scopeChangeRequestRepository.findByIdAndPartnerOrderSalesOrderId(requestId, salesOrderId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.SCOPE_REQUEST_NOT_FOUND));
        if (!scopeRequest.isOpen()) {
            throw new ApplicationException(OrderErrorCode.SCOPE_REQUEST_CLOSED);
        }
        scopeRequest.close(adminUserId, note.trim(), null);

        auditLogRecorder.record(adminUserId, PlatformAdminAction.CLOSE_SCOPE_CHANGE_REQUEST, null,
                scopeRequest.getPartnerOrder().getOrganization().getId(),
                "salesOrderId=" + salesOrderId + ", requestId=" + requestId + ", note=" + note);
        return SalesOrderChangeDto.Response.ScopeRequestSummary.from(scopeRequest);
    }

    /** 파트너가 받은 발주의 범위 변경과 요청 — 접근 검사는 호출하는 쪽({@link PartnerOrderResponseService})이 한다. */
    public SalesOrderChangeDto.Response.PartnerOverview findPartnerChanges(PartnerOrder partnerOrder) {
        List<SalesOrderChange> changes = salesOrderChangeRepository.findAllByPartnerOrderIdOrderByIdDesc(partnerOrder.getId());
        Map<Long, List<SalesOrderChangeLine>> lines = linesByChange(changes);
        return new SalesOrderChangeDto.Response.PartnerOverview(
                changes.stream()
                        .map(change -> SalesOrderChangeDto.Response.PartnerChange.of(change, lines.getOrDefault(change.getId(), List.of())))
                        .toList(),
                scopeChangeRequestRepository.findAllByPartnerOrderIdOrderByIdDesc(partnerOrder.getId()).stream()
                        .map(SalesOrderChangeDto.Response.ScopeRequestSummary::from)
                        .toList()
        );
    }

    @Transactional
    public SalesOrderChange acceptByPartner(PartnerOrder partnerOrder, Long changeId, Long userId) {
        SalesOrderChange change = findRespondableOrThrow(partnerOrder, changeId);
        change.acceptByPartner(userId);
        applyIfReady(change);
        orderNotificationService.scopeChangeResponded(change);
        return change;
    }

    @Transactional
    public SalesOrderChange rejectByPartner(PartnerOrder partnerOrder, Long changeId, Long userId, String reason) {
        SalesOrderChange change = findRespondableOrThrow(partnerOrder, changeId);
        change.rejectByPartner(userId, reason.trim());
        orderNotificationService.scopeChangeResponded(change);
        return change;
    }

    @Transactional
    public ScopeChangeRequest submitScopeRequest(PartnerOrder partnerOrder, Long userId, String content) {
        if (!partnerOrder.isCancellableAfterAcceptance()) {
            throw new ApplicationException(OrderErrorCode.SCOPE_REQUEST_NOT_ALLOWED);
        }
        ScopeChangeRequest request = scopeChangeRequestRepository.save(ScopeChangeRequest.builder()
                .partnerOrder(partnerOrder)
                .content(content.trim())
                .requestedBy(userId)
                .build());
        orderNotificationService.scopeChangeRequested(request);
        return request;
    }

    /** 발주를 수락 후 취소(파트너 교체)하면 그 발주에 걸린 진행 중 변경도 취소한다 — 새 파트너 기준으로 다시 제안한다. */
    @Transactional
    public void cancelProposedForPartnerOrder(Long partnerOrderId, String reason) {
        salesOrderChangeRepository.findAllByPartnerOrderIdAndStatus(partnerOrderId, SalesOrderChangeStatus.PROPOSED)
                .forEach(change -> change.cancel(reason));
    }

    /** 진행 중인 변경이 있으면 새 발주를 내지 않는다 — 변경 전 범위로 발주가 나가지 않게 한다. */
    public boolean hasProposedChange(Long salesOrderId) {
        return salesOrderChangeRepository.existsBySalesOrderIdAndStatus(salesOrderId, SalesOrderChangeStatus.PROPOSED);
    }

    private void applyIfReady(SalesOrderChange change) {
        if (!change.isReadyToApply()) {
            return;
        }
        SalesOrder salesOrder = change.getSalesOrder();
        List<SalesOrderLine> previousLines = salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(salesOrder.getId());
        List<SalesOrderChangeLine> newLines =
                salesOrderChangeLineRepository.findAllBySalesOrderChangeIdOrderByDisplayOrder(change.getId());

        salesOrder.applyChange(change);
        salesOrderLineRepository.deleteAllBySalesOrderId(salesOrder.getId());
        for (SalesOrderChangeLine line : newLines) {
            salesOrderLineRepository.save(SalesOrderLine.builder()
                    .salesOrder(salesOrder)
                    .lineType(line.getLineType())
                    .unitProduct(line.getUnitProduct())
                    .name(line.getName())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .amount(line.getAmount())
                    .taxCode(line.getTaxCode())
                    .taxRatePercent(line.getTaxRatePercent())
                    .taxAmount(line.getTaxAmount())
                    .displayOrder(line.getDisplayOrder())
                    .customerPriceId(line.getCustomerPriceId())
                    .partnerUnitPrice(line.getPartnerUnitPrice())
                    .build());
        }

        PartnerOrder partnerOrder = change.getPartnerOrder();
        if (partnerOrder != null) {
            partnerOrder.applyScopeChange(
                    change.getTabletCount(), change.getWifiCount(), change.getOnsiteStaffPersonDays(), change.scopeExtras(),
                    change.getCustomItemPartnerAmount(), change.getPartnerAmount()
            );
            platformOrderCeremonyService.applyScopeChange(
                    partnerOrder.getCeremony(), change.getLocation(), change.getStartsAt(), change.getEndsAt(),
                    addedOptions(previousLines, newLines)
            );
        }
        change.markApplied();
        orderNotificationService.scopeChangeApplied(change);
    }

    /** 단위 상품별로 늘어난 수량만 — 행사에는 0원 자동 승인 구매로 더한다. */
    private static List<PlatformOrderCeremonyCommand.ScopeOption> addedOptions(
            List<SalesOrderLine> previousLines,
            List<SalesOrderChangeLine> newLines
    ) {
        Map<Long, Integer> previous = previousLines.stream()
                .filter(line -> line.getLineType() == SalesOrderLineType.UNIT_PRODUCT)
                .collect(Collectors.toMap(line -> line.getUnitProduct().getId(), SalesOrderLine::getQuantity, Integer::sum));
        Map<Long, Integer> next = new LinkedHashMap<>();
        Map<Long, String> taxCodes = new HashMap<>();
        newLines.stream()
                .filter(line -> line.getLineType() == SalesOrderLineType.UNIT_PRODUCT)
                .forEach(line -> {
                    next.merge(line.getUnitProduct().getId(), line.getQuantity(), Integer::sum);
                    taxCodes.putIfAbsent(line.getUnitProduct().getId(), line.getTaxCode());
                });
        List<PlatformOrderCeremonyCommand.ScopeOption> added = new ArrayList<>();
        next.forEach((unitProductId, quantity) -> {
            int delta = quantity - previous.getOrDefault(unitProductId, 0);
            if (delta > 0) {
                added.add(new PlatformOrderCeremonyCommand.ScopeOption(unitProductId, delta, taxCodes.get(unitProductId)));
            }
        });
        return added;
    }

    private SalesOrderChange findProposedOrThrow(Long salesOrderId, Long changeId) {
        SalesOrderChange change = salesOrderChangeRepository.findByIdAndSalesOrderId(changeId, salesOrderId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
        if (!change.isProposed()) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_NOT_PROPOSED);
        }
        return change;
    }

    private SalesOrderChange findRespondableOrThrow(PartnerOrder partnerOrder, Long changeId) {
        SalesOrderChange change = salesOrderChangeRepository.findByIdAndPartnerOrderId(changeId, partnerOrder.getId())
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
        if (!change.isProposed()) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_NOT_PROPOSED);
        }
        if (change.getPartnerRespondedAt() != null) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_CHANGE_ALREADY_RESPONDED);
        }
        return change;
    }

    private SalesOrderChangeDto.Response.AdminChange toAdminChange(SalesOrderChange change) {
        return SalesOrderChangeDto.Response.AdminChange.of(
                change, salesOrderChangeLineRepository.findAllBySalesOrderChangeIdOrderByDisplayOrder(change.getId())
        );
    }

    private Map<Long, List<SalesOrderChangeLine>> linesByChange(List<SalesOrderChange> changes) {
        if (changes.isEmpty()) {
            return Map.of();
        }
        return salesOrderChangeLineRepository.findAllBySalesOrderChangeIdInOrderByDisplayOrder(
                changes.stream().map(SalesOrderChange::getId).toList()
        ).stream().collect(Collectors.groupingBy(line -> line.getSalesOrderChange().getId()));
    }

    private void checkCanManage(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_SALES_ORDER_MANAGE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
