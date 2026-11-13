package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.CustomerInvoiceDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceType;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객 청구·수납 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 7단계,
 * D12(선금+잔금), D16(세금계산서 발행 기록만).
 *
 * <ul>
 *   <li>선금: 고객이 수락한 수주에 수주 금액 × 선금 비율(공급가·부가세 각각, 통화 반올림)을 청구한다. 청구할 때
 *       퍼센트나 정액(부가세 포함 총액)으로 바꿀 수 있다.</li>
 *   <li>잔금: 수락된 발주의 행사가 완료된 뒤 수주 금액에서 선금을 뺀 나머지를 청구한다. 선금 없이
 *       잔금만 청구하면 전액이다. 잔금을 청구한 뒤에는 선금을 새로 청구하지 않는다.</li>
 *   <li>입금·세금계산서는 시스템 밖에서 처리하고 기록만 한다. 입금 전 청구는 취소할 수 있다(잔금이
 *       있으면 선금은 잔금을 먼저 취소해야 한다 — 잔금 금액이 선금을 빼고 계산됐기 때문이다).</li>
 * </ul>
 *
 * <p>쓰기는 {@code ACTION_CUSTOMER_INVOICE_MANAGE}(PLATFORM_OPS 이상)가 필요하다. 파트너 지급과 무관하다(D12).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerInvoiceService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final SalesOrderService salesOrderService;
    private final CustomerBillingPolicy customerBillingPolicy;
    private final MoneyCalculator moneyCalculator;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public Page<CustomerInvoiceDto.Response.InvoiceSummary> findInvoices(
            CustomerInvoiceStatus status,
            Long customerId,
            boolean overdueOnly,
            Pageable pageable
    ) {
        LocalDate today = today();
        return customerInvoiceRepository.search(status, customerId, overdueOnly ? today : null, pageable)
                .map(invoice -> CustomerInvoiceDto.Response.InvoiceSummary.from(invoice, today));
    }

    public CustomerInvoiceDto.Response.SalesOrderBilling retrieveSalesOrderBilling(Long salesOrderId) {
        return toBilling(salesOrderService.findSalesOrderOrThrow(salesOrderId));
    }

    @Transactional
    public CustomerInvoiceDto.Response.SalesOrderBilling issueInvoice(
            Long salesOrderId,
            Long adminUserId,
            String actingPlatformRole,
            CustomerInvoiceDto.Request.Issue request
    ) {
        checkCanManage(actingPlatformRole);
        SalesOrder salesOrder = salesOrderService.findSalesOrderOrThrow(salesOrderId);
        if (salesOrder.getStatus() != SalesOrderStatus.ACCEPTED) {
            throw new ApplicationException(OrderErrorCode.INVOICE_SALES_ORDER_NOT_ACCEPTED);
        }
        LocalDate today = today();
        if (request.getDueDate().isBefore(today)) {
            throw new ApplicationException(OrderErrorCode.INVOICE_DUE_DATE_INVALID);
        }
        List<CustomerInvoice> active = activeInvoices(salesOrderId);
        if (hasType(active, request.getInvoiceType())) {
            throw new ApplicationException(OrderErrorCode.INVOICE_ALREADY_ISSUED);
        }

        BigDecimal supplyAmount;
        BigDecimal vatAmount;
        CurrencyPolicy currencyPolicy = salesOrder.currencyPolicy();
        if (request.getInvoiceType() == CustomerInvoiceType.ADVANCE) {
            if (!customerBillingPolicy.isAdvanceEnabled()) {
                throw new ApplicationException(OrderErrorCode.INVOICE_ADVANCE_DISABLED);
            }
            if (hasType(active, CustomerInvoiceType.BALANCE)) {
                throw new ApplicationException(OrderErrorCode.INVOICE_ADVANCE_AFTER_BALANCE);
            }
            if (request.getAdvanceAmountType() == DiscountType.FIXED_AMOUNT) {
                BigDecimal total = moneyCalculator.normalize(requireAdvanceValue(request), currencyPolicy);
                BigDecimal contractTotal = salesOrder.getSupplyAmount().add(salesOrder.getVatAmount());
                if (total.signum() <= 0 || total.compareTo(contractTotal) >= 0) {
                    throw new ApplicationException(OrderErrorCode.INVOICE_ADVANCE_AMOUNT_INVALID);
                }
                // 부가세 포함 총액을 수주의 공급가:부가세 비율로 나눈다 — 반올림 차이는 부가세에 둬서 총액이 입력값과 같다.
                supplyAmount = moneyCalculator.normalize(
                        total.multiply(salesOrder.getSupplyAmount()).divide(contractTotal, MathContext.DECIMAL64), currencyPolicy);
                vatAmount = total.subtract(supplyAmount);
            } else {
                BigDecimal rate = advanceRate(request);
                supplyAmount = moneyCalculator.normalize(salesOrder.getSupplyAmount().multiply(rate), currencyPolicy);
                vatAmount = moneyCalculator.normalize(salesOrder.getVatAmount().multiply(rate), currencyPolicy);
            }
        } else {
            if (!partnerOrderRepository.existsCompletedBySalesOrderId(salesOrderId)) {
                throw new ApplicationException(OrderErrorCode.INVOICE_BALANCE_BEFORE_COMPLETION);
            }
            supplyAmount = salesOrder.getSupplyAmount().subtract(sum(active, CustomerInvoice::getSupplyAmount));
            vatAmount = salesOrder.getVatAmount().subtract(sum(active, CustomerInvoice::getVatAmount));
        }
        if (supplyAmount.add(vatAmount).signum() <= 0) {
            throw new ApplicationException(OrderErrorCode.INVOICE_NOTHING_TO_BILL);
        }

        CustomerInvoice invoice = customerInvoiceRepository.save(CustomerInvoice.builder()
                .salesOrder(salesOrder)
                .invoiceType(request.getInvoiceType())
                .supplyAmount(supplyAmount)
                .vatAmount(vatAmount)
                .issueDate(today)
                .dueDate(request.getDueDate())
                .issuedBy(adminUserId)
                .build());

        auditLogRecorder.record(adminUserId, PlatformAdminAction.ISSUE_CUSTOMER_INVOICE, null, null,
                "customerInvoiceId=" + invoice.getId() + ", salesOrderId=" + salesOrderId
                        + ", type=" + invoice.getInvoiceType() + ", totalAmount=" + invoice.getTotalAmount());
        return toBilling(salesOrder);
    }

    @Transactional
    public CustomerInvoiceDto.Response.InvoiceSummary recordPayment(
            Long invoiceId,
            Long adminUserId,
            String actingPlatformRole,
            CustomerInvoiceDto.Request.RecordPayment request
    ) {
        checkCanManage(actingPlatformRole);
        CustomerInvoice invoice = findIssuedOrThrow(invoiceId);
        LocalDate today = today();
        if (request.getPaidDate().isAfter(today)) {
            throw new ApplicationException(OrderErrorCode.INVOICE_PAID_DATE_INVALID);
        }
        invoice.recordPayment(adminUserId, request.getPaidDate(), request.getPaidAmount(), trimToNull(request.getPaymentMemo()));

        auditLogRecorder.record(adminUserId, PlatformAdminAction.RECORD_CUSTOMER_PAYMENT, null, null,
                "customerInvoiceId=" + invoiceId + ", paidDate=" + request.getPaidDate()
                        + ", paidAmount=" + request.getPaidAmount());
        return CustomerInvoiceDto.Response.InvoiceSummary.from(invoice, today);
    }

    @Transactional
    public CustomerInvoiceDto.Response.InvoiceSummary recordTaxInvoice(
            Long invoiceId,
            Long adminUserId,
            String actingPlatformRole,
            CustomerInvoiceDto.Request.RecordTaxInvoice request
    ) {
        checkCanManage(actingPlatformRole);
        CustomerInvoice invoice = findInvoiceOrThrow(invoiceId);
        if (!invoice.isActive()) {
            throw new ApplicationException(OrderErrorCode.INVOICE_CANCELLED);
        }
        invoice.recordTaxInvoice(request.getIssuedDate(), request.getApprovalNumber().trim());

        auditLogRecorder.record(adminUserId, PlatformAdminAction.RECORD_CUSTOMER_TAX_INVOICE, null, null,
                "customerInvoiceId=" + invoiceId + ", issuedDate=" + request.getIssuedDate()
                        + ", approvalNumber=" + invoice.getTaxInvoiceApprovalNumber());
        return CustomerInvoiceDto.Response.InvoiceSummary.from(invoice, today());
    }

    @Transactional
    public CustomerInvoiceDto.Response.InvoiceSummary cancelInvoice(
            Long invoiceId,
            Long adminUserId,
            String actingPlatformRole,
            String reason
    ) {
        checkCanManage(actingPlatformRole);
        CustomerInvoice invoice = findIssuedOrThrow(invoiceId);
        if (invoice.getInvoiceType() == CustomerInvoiceType.ADVANCE
                && hasType(activeInvoices(invoice.getSalesOrder().getId()), CustomerInvoiceType.BALANCE)) {
            throw new ApplicationException(OrderErrorCode.INVOICE_BALANCE_EXISTS);
        }
        invoice.cancel(reason.trim());

        auditLogRecorder.record(adminUserId, PlatformAdminAction.CANCEL_CUSTOMER_INVOICE, null, null,
                "customerInvoiceId=" + invoiceId + ", reason=" + reason);
        return CustomerInvoiceDto.Response.InvoiceSummary.from(invoice, today());
    }

    /** 퍼센트로 지정했으면 그 비율, 아니면 기본 선금 비율. */
    private BigDecimal advanceRate(CustomerInvoiceDto.Request.Issue request) {
        if (request.getAdvanceAmountType() != DiscountType.PERCENT) {
            return customerBillingPolicy.advancePaymentRate();
        }
        BigDecimal percent = requireAdvanceValue(request);
        if (percent.signum() <= 0 || percent.compareTo(ONE_HUNDRED) >= 0) {
            throw new ApplicationException(OrderErrorCode.INVOICE_ADVANCE_AMOUNT_INVALID);
        }
        return percent.divide(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    private static BigDecimal requireAdvanceValue(CustomerInvoiceDto.Request.Issue request) {
        if (request.getAdvanceValue() == null) {
            throw new ApplicationException(OrderErrorCode.INVOICE_ADVANCE_AMOUNT_INVALID);
        }
        return request.getAdvanceValue();
    }

    private CustomerInvoiceDto.Response.SalesOrderBilling toBilling(SalesOrder salesOrder) {
        LocalDate today = today();
        List<CustomerInvoice> invoices = customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(salesOrder.getId());
        List<CustomerInvoice> active = invoices.stream().filter(CustomerInvoice::isActive).toList();
        boolean accepted = salesOrder.getStatus() == SalesOrderStatus.ACCEPTED;
        boolean eventCompleted = partnerOrderRepository.existsCompletedBySalesOrderId(salesOrder.getId());
        BigDecimal billed = sum(active, CustomerInvoice::getTotalAmount);
        BigDecimal paid = sum(active.stream().filter(invoice -> invoice.getStatus() == CustomerInvoiceStatus.PAID).toList(),
                CustomerInvoice::getTotalAmount);
        return new CustomerInvoiceDto.Response.SalesOrderBilling(
                salesOrder.getId(),
                salesOrder.getCurrencyCode(),
                customerBillingPolicy.advancePaymentRate(),
                salesOrder.getTotalAmount(),
                billed,
                paid,
                billed.subtract(paid),
                eventCompleted,
                accepted && customerBillingPolicy.isAdvanceEnabled()
                        && !hasType(active, CustomerInvoiceType.ADVANCE) && !hasType(active, CustomerInvoiceType.BALANCE),
                accepted && eventCompleted && !hasType(active, CustomerInvoiceType.BALANCE),
                invoices.stream().map(invoice -> CustomerInvoiceDto.Response.InvoiceSummary.from(invoice, today)).toList()
        );
    }

    private List<CustomerInvoice> activeInvoices(Long salesOrderId) {
        return customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(salesOrderId).stream()
                .filter(CustomerInvoice::isActive)
                .toList();
    }

    private CustomerInvoice findInvoiceOrThrow(Long invoiceId) {
        return customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.INVOICE_NOT_FOUND));
    }

    private CustomerInvoice findIssuedOrThrow(Long invoiceId) {
        CustomerInvoice invoice = findInvoiceOrThrow(invoiceId);
        if (!invoice.isIssued()) {
            throw new ApplicationException(OrderErrorCode.INVOICE_NOT_ISSUED);
        }
        return invoice;
    }

    private void checkCanManage(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_CUSTOMER_INVOICE_MANAGE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private static boolean hasType(List<CustomerInvoice> invoices, CustomerInvoiceType type) {
        return invoices.stream().anyMatch(invoice -> invoice.getInvoiceType() == type);
    }

    private static BigDecimal sum(List<CustomerInvoice> invoices, Function<CustomerInvoice, BigDecimal> amount) {
        return invoices.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate today() {
        return LocalDate.now(SalesOrderService.PLATFORM_ZONE);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
