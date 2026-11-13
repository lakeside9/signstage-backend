package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.CustomerCancellationDto;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminSalesOrderDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceType;
import com.eformworks.signstage.backend.feature.order.entity.CustomerRefund;
import com.eformworks.signstage.backend.feature.order.entity.PartnerCompensation;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.CustomerRefundRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerCompensationRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수주 고객 취소 — signstage-docs business/platform-order-pending-items.md C01~C04(2026-11-13 권장안으로 결정).
 * 고객이 수락한 수주를 고객 사정으로 취소할 때 한 번에 정리한다.
 *
 * <ol>
 *   <li>응답 대기·수락 발주를 취소한다(기한이 지난 응답 대기 발주는 만료로 확정). 수락 발주는 발주 금액 × 보상
 *       비율만큼 취소 보상을 남겨 다음 월 정산에 더한다(C04).</li>
 *   <li>진행 중인 범위 변경을 취소하고, 발주에 연결된 행사(파트너 교체로 넘겨받기를 기다리던 행사 포함)를
 *       취소 상태로 닫는다(C01).</li>
 *   <li>입금 전 청구를 취소하고, 받은 금액과 취소 수수료(계약 공급가 × 비율, C03)를 비교해 차액을 환불 대기로
 *       남기거나 취소 수수료 청구로 발행한다.</li>
 *   <li>수주를 고객 취소로 닫고 파트너에게 알린다.</li>
 * </ol>
 *
 * <p>행사가 완료됐거나 잔금을 청구했으면 거부한다. 수락한 발주가 있으면 파트너 교체 권한(PLATFORM_SUPER)이,
 * 청구가 있거나 수수료가 생기면 고객 청구 권한이 함께 필요하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerCancellationService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    /** 취소 수수료 청구의 입금 기한(발행일부터). */
    private static final int FEE_INVOICE_DUE_DAYS = 14;

    private final SalesOrderService salesOrderService;
    private final PartnerOrderRepository partnerOrderRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final CustomerRefundRepository customerRefundRepository;
    private final PartnerCompensationRepository partnerCompensationRepository;
    private final PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    private final SalesOrderChangeService salesOrderChangeService;
    private final PlatformOrderCeremonyService platformOrderCeremonyService;
    private final OrderNotificationService orderNotificationService;
    private final CustomerCancellationPolicy customerCancellationPolicy;
    private final MoneyCalculator moneyCalculator;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public CustomerCancellationDto.Response.Preview preview(Long salesOrderId, String actingPlatformRole) {
        checkAllowed(actingPlatformRole, "ACTION_SALES_ORDER_MANAGE");
        return plan(salesOrderService.findSalesOrderOrThrow(salesOrderId)).toPreview();
    }

    @Transactional
    public PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail cancelByCustomer(
            Long salesOrderId,
            Long adminUserId,
            String actingPlatformRole,
            String reason
    ) {
        checkAllowed(actingPlatformRole, "ACTION_SALES_ORDER_MANAGE");
        SalesOrder salesOrder = salesOrderService.findSalesOrderOrThrow(salesOrderId);
        Plan plan = plan(salesOrder);
        if (plan.partnerOrders.stream().anyMatch(order -> order.getStatus() == PartnerOrderStatus.ACCEPTED)) {
            checkAllowed(actingPlatformRole, "ACTION_PARTNER_ORDER_REPLACE");
        }
        if (!plan.invoices.isEmpty() || plan.feeAmount.signum() > 0) {
            checkAllowed(actingPlatformRole, "ACTION_CUSTOMER_INVOICE_MANAGE");
        }
        String trimmedReason = reason.trim();
        LocalDate today = plan.today;

        // 1. 발주와 취소 보상
        for (PartnerOrder partnerOrder : plan.partnerOrders) {
            if (partnerOrder.getStatus() == PartnerOrderStatus.ISSUED && partnerOrder.isOverdue(plan.now)) {
                partnerOrder.expire();
                continue;
            }
            if (partnerOrder.getStatus() != PartnerOrderStatus.ISSUED && partnerOrder.getStatus() != PartnerOrderStatus.ACCEPTED) {
                continue;
            }
            BigDecimal compensation = plan.compensationFor(partnerOrder);
            partnerOrder.cancel("고객 취소: " + trimmedReason);
            if (compensation.signum() > 0) {
                PartnerCompensation saved = partnerCompensationRepository.save(PartnerCompensation.builder()
                        .partnerOrder(partnerOrder)
                        .amount(compensation)
                        .ratePercent(plan.compensationRatePercent)
                        .reason(trimmedReason)
                        .occurredOn(today)
                        .build());
                partnerLedgerEntryRepository.save(PartnerLedgerEntry.compensation(saved));
            }
            orderNotificationService.partnerOrderCancelledByCustomer(partnerOrder, trimmedReason, compensation);
        }

        // 2. 범위 변경과 행사
        salesOrderChangeService.cancelProposedForSalesOrder(salesOrderId, "고객 취소");
        plan.ceremonies.forEach(platformOrderCeremonyService::cancelForCustomer);

        // 3. 청구·환불·취소 수수료
        plan.invoices.stream().filter(CustomerInvoice::isIssued).forEach(invoice -> invoice.cancel("고객 취소"));
        if (plan.refundAmount().signum() > 0) {
            customerRefundRepository.save(CustomerRefund.builder()
                    .salesOrder(salesOrder)
                    .amount(plan.refundAmount())
                    .reason("고객 취소(수수료 " + plan.feeRatePercent.stripTrailingZeros().toPlainString() + "%): " + trimmedReason)
                    .build());
        }
        if (plan.additionalFeeAmount().signum() > 0) {
            customerInvoiceRepository.save(CustomerInvoice.builder()
                    .salesOrder(salesOrder)
                    .invoiceType(CustomerInvoiceType.CANCELLATION_FEE)
                    .supplyAmount(plan.additionalFeeAmount())
                    .vatAmount(BigDecimal.ZERO)
                    .issueDate(today)
                    .dueDate(today.plusDays(FEE_INVOICE_DUE_DAYS))
                    .issuedBy(adminUserId)
                    .build());
        }

        // 4. 수주
        salesOrder.cancelByCustomer(trimmedReason, plan.feeRatePercent, plan.feeAmount);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CUSTOMER_CANCEL_SALES_ORDER, null, null,
                "salesOrderId=" + salesOrderId + ", daysBeforeEvent=" + plan.daysBeforeEvent
                        + ", feeRatePercent=" + plan.feeRatePercent + ", feeAmount=" + plan.feeAmount
                        + ", paidAmount=" + plan.paidAmount + ", refundAmount=" + plan.refundAmount()
                        + ", additionalFeeAmount=" + plan.additionalFeeAmount() + ", reason=" + trimmedReason);
        return salesOrderService.retrieveSalesOrder(salesOrderId);
    }

    private Plan plan(SalesOrder salesOrder) {
        if (salesOrder.getStatus() != SalesOrderStatus.ACCEPTED) {
            throw new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_ACCEPTED);
        }
        if (partnerOrderRepository.existsCompletedBySalesOrderId(salesOrder.getId())) {
            throw new ApplicationException(OrderErrorCode.CUSTOMER_CANCEL_EVENT_COMPLETED);
        }
        List<CustomerInvoice> invoices = customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(salesOrder.getId()).stream()
                .filter(CustomerInvoice::isActive)
                .toList();
        if (invoices.stream().anyMatch(invoice -> invoice.getInvoiceType() == CustomerInvoiceType.BALANCE)) {
            throw new ApplicationException(OrderErrorCode.CUSTOMER_CANCEL_BALANCE_ISSUED);
        }

        LocalDateTime now = LocalDateTime.now(SalesOrderService.PLATFORM_ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate eventDate = salesOrder.getStartsAt() != null ? salesOrder.getStartsAt().toLocalDate() : null;
        Long daysBeforeEvent = eventDate != null ? ChronoUnit.DAYS.between(today, eventDate) : null;
        BigDecimal feeRatePercent = customerCancellationPolicy.feeRatePercent(daysBeforeEvent);
        BigDecimal compensationRatePercent = customerCancellationPolicy.compensationRatePercent(daysBeforeEvent);
        CurrencyPolicy currencyPolicy = salesOrder.currencyPolicy();
        BigDecimal feeAmount = moneyCalculator.normalize(
                salesOrder.getSupplyAmount().multiply(feeRatePercent).divide(ONE_HUNDRED, MathContext.DECIMAL64), currencyPolicy);
        BigDecimal paidAmount = invoices.stream()
                .filter(invoice -> invoice.getStatus() == CustomerInvoiceStatus.PAID)
                .map(CustomerInvoice::getPaidAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PartnerOrder> partnerOrders = partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(salesOrder.getId());
        List<Ceremony> ceremonies = partnerOrders.stream()
                .map(PartnerOrder::getCeremony)
                .filter(Objects::nonNull)
                .distinct()
                .filter(ceremony -> !ceremony.isClosed())
                .toList();
        return new Plan(salesOrder, now, today, eventDate, daysBeforeEvent, feeRatePercent, compensationRatePercent, feeAmount,
                paidAmount, invoices, partnerOrders, ceremonies, currencyPolicy);
    }

    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    /** 미리보기와 실행이 같은 계산을 쓰도록 모은 취소 계획. */
    private final class Plan {

        private final SalesOrder salesOrder;
        private final LocalDateTime now;
        private final LocalDate today;
        private final LocalDate eventDate;
        private final Long daysBeforeEvent;
        private final BigDecimal feeRatePercent;
        private final BigDecimal compensationRatePercent;
        private final BigDecimal feeAmount;
        private final BigDecimal paidAmount;
        private final List<CustomerInvoice> invoices;
        private final List<PartnerOrder> partnerOrders;
        private final List<Ceremony> ceremonies;
        private final CurrencyPolicy currencyPolicy;

        private Plan(
                SalesOrder salesOrder, LocalDateTime now, LocalDate today, LocalDate eventDate, Long daysBeforeEvent,
                BigDecimal feeRatePercent, BigDecimal compensationRatePercent, BigDecimal feeAmount, BigDecimal paidAmount,
                List<CustomerInvoice> invoices, List<PartnerOrder> partnerOrders, List<Ceremony> ceremonies,
                CurrencyPolicy currencyPolicy
        ) {
            this.salesOrder = salesOrder;
            this.now = now;
            this.today = today;
            this.eventDate = eventDate;
            this.daysBeforeEvent = daysBeforeEvent;
            this.feeRatePercent = feeRatePercent;
            this.compensationRatePercent = compensationRatePercent;
            this.feeAmount = feeAmount;
            this.paidAmount = paidAmount;
            this.invoices = invoices;
            this.partnerOrders = partnerOrders;
            this.ceremonies = ceremonies;
            this.currencyPolicy = currencyPolicy;
        }

        private BigDecimal refundAmount() {
            return paidAmount.subtract(feeAmount).max(BigDecimal.ZERO);
        }

        private BigDecimal additionalFeeAmount() {
            return feeAmount.subtract(paidAmount).max(BigDecimal.ZERO);
        }

        /** 수락한 발주만 보상한다 — 응답 대기 발주는 파트너가 아직 준비하지 않았다. */
        private BigDecimal compensationFor(PartnerOrder partnerOrder) {
            if (partnerOrder.getStatus() != PartnerOrderStatus.ACCEPTED) {
                return BigDecimal.ZERO;
            }
            return moneyCalculator.normalize(
                    partnerOrder.getTotalAmount().multiply(compensationRatePercent).divide(ONE_HUNDRED, MathContext.DECIMAL64),
                    currencyPolicy);
        }

        private CustomerCancellationDto.Response.Preview toPreview() {
            List<CustomerCancellationDto.Response.PartnerImpact> impacts = partnerOrders.stream()
                    .filter(order -> order.getStatus() == PartnerOrderStatus.ISSUED && !order.isOverdue(now)
                            || order.getStatus() == PartnerOrderStatus.ACCEPTED)
                    .map(order -> new CustomerCancellationDto.Response.PartnerImpact(
                            order.getId(),
                            order.getOrganization().getName(),
                            order.getStatus().name(),
                            order.getTotalAmount(),
                            order.getStatus() == PartnerOrderStatus.ACCEPTED ? compensationRatePercent : BigDecimal.ZERO,
                            compensationFor(order)
                    ))
                    .toList();
            return new CustomerCancellationDto.Response.Preview(
                    salesOrder.getId(),
                    salesOrder.getCurrencyCode(),
                    eventDate,
                    daysBeforeEvent,
                    feeRatePercent,
                    feeAmount,
                    paidAmount,
                    refundAmount(),
                    additionalFeeAmount(),
                    (int) invoices.stream().filter(CustomerInvoice::isIssued).count(),
                    impacts,
                    ceremonies.stream().map(Ceremony::getId).toList(),
                    salesOrderChangeService.hasProposedChange(salesOrder.getId())
            );
        }
    }
}
