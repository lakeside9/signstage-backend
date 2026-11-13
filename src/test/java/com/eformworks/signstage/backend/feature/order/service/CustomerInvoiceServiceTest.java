package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.CustomerInvoiceDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceType;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderAcceptanceChannel;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link CustomerInvoiceService} 단위 테스트 — 선금·잔금 청구, 입금·취소 규칙(D12·D16). */
@ExtendWith(MockitoExtension.class)
class CustomerInvoiceServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long SALES_ORDER_ID = 100L;
    private static final String ROLE = "PLATFORM_OPS";

    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    private CustomerInvoiceService customerInvoiceService;
    private SalesOrder salesOrder;
    private final List<CustomerInvoice> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        customerInvoiceService = new CustomerInvoiceService(
                customerInvoiceRepository, partnerOrderRepository, salesOrderService,
                new CustomerBillingPolicy(new BigDecimal("0.3")), new MoneyCalculator(), rolePermissionService, auditLogRecorder
        );
        // 공급가 3,000,000 · 부가세 300,000
        salesOrder = OrderFixtures.salesOrder(SALES_ORDER_ID, OrderFixtures.customer(1L), OrderFixtures.plan(1L));
        salesOrder.accept("김담당", SalesOrderAcceptanceChannel.PLATFORM_RECORDED);
        lenient().when(rolePermissionService.isAllowed(ROLE, "ACTION_CUSTOMER_INVOICE_MANAGE")).thenReturn(true);
        lenient().when(salesOrderService.findSalesOrderOrThrow(SALES_ORDER_ID)).thenReturn(salesOrder);
        lenient().when(customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(SALES_ORDER_ID))
                .thenAnswer(invocation -> List.copyOf(stored));
        lenient().when(customerInvoiceRepository.save(any())).thenAnswer(invocation -> {
            CustomerInvoice invoice = invocation.getArgument(0);
            ReflectionTestUtils.setField(invoice, "id", 500L + stored.size());
            stored.add(invoice);
            return invoice;
        });
    }

    @Test
    @DisplayName("선금은 공급가·부가세에 각각 선금 비율을 곱해 청구한다")
    void issueAdvance() {
        CustomerInvoiceDto.Response.SalesOrderBilling billing = issue(CustomerInvoiceType.ADVANCE);

        CustomerInvoice advance = stored.getFirst();
        assertThat(advance.getSupplyAmount()).isEqualByComparingTo("900000");
        assertThat(advance.getVatAmount()).isEqualByComparingTo("90000");
        assertThat(advance.getTotalAmount()).isEqualByComparingTo("990000");
        assertThat(billing.getBilledAmount()).isEqualByComparingTo("990000");
        assertThat(billing.getOutstandingAmount()).isEqualByComparingTo("990000");
        assertThat(billing.isAdvanceIssuable()).isFalse();
        assertThat(billing.isBalanceIssuable()).isFalse();
    }

    @Test
    @DisplayName("선금을 퍼센트로 지정하면 그 비율로 공급가·부가세를 각각 계산한다")
    void issueAdvance_withPercent() {
        issueAdvance(CustomerInvoiceType.ADVANCE, DiscountType.PERCENT, "50");

        CustomerInvoice advance = stored.getFirst();
        assertThat(advance.getSupplyAmount()).isEqualByComparingTo("1500000");
        assertThat(advance.getVatAmount()).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("선금을 정액으로 지정하면 부가세 포함 총액을 수주의 공급가:부가세 비율로 나눈다")
    void issueAdvance_withFixedAmount() {
        issueAdvance(CustomerInvoiceType.ADVANCE, DiscountType.FIXED_AMOUNT, "1000000");

        CustomerInvoice advance = stored.getFirst();
        assertThat(advance.getSupplyAmount()).isEqualByComparingTo("909091");
        assertThat(advance.getVatAmount()).isEqualByComparingTo("90909");
        assertThat(advance.getTotalAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("선금은 수주 금액 이상이거나 100% 이상일 수 없다")
    void issueAdvance_rejectsAmountNotBelowContract() {
        assertErrorCode(() -> issueAdvance(CustomerInvoiceType.ADVANCE, DiscountType.FIXED_AMOUNT, "3300000"),
                OrderErrorCode.INVOICE_ADVANCE_AMOUNT_INVALID);
        assertErrorCode(() -> issueAdvance(CustomerInvoiceType.ADVANCE, DiscountType.PERCENT, "100"),
                OrderErrorCode.INVOICE_ADVANCE_AMOUNT_INVALID);
        assertErrorCode(() -> issueAdvance(CustomerInvoiceType.ADVANCE, DiscountType.PERCENT, null),
                OrderErrorCode.INVOICE_ADVANCE_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("같은 종류의 청구는 하나만 발행한다")
    void issueAdvance_rejectsDuplicate() {
        issue(CustomerInvoiceType.ADVANCE);

        assertErrorCode(() -> issue(CustomerInvoiceType.ADVANCE), OrderErrorCode.INVOICE_ALREADY_ISSUED);
    }

    @Test
    @DisplayName("고객이 수락하지 않은 수주는 청구할 수 없다")
    void issue_rejectsNotAcceptedSalesOrder() {
        SalesOrder quoted = OrderFixtures.salesOrder(SALES_ORDER_ID, OrderFixtures.customer(1L), OrderFixtures.plan(1L));
        given(salesOrderService.findSalesOrderOrThrow(SALES_ORDER_ID)).willReturn(quoted);

        assertErrorCode(() -> issue(CustomerInvoiceType.ADVANCE), OrderErrorCode.INVOICE_SALES_ORDER_NOT_ACCEPTED);
    }

    @Test
    @DisplayName("행사 완료 전에는 잔금을 청구할 수 없다")
    void issueBalance_requiresCompletedEvent() {
        given(partnerOrderRepository.existsCompletedBySalesOrderId(SALES_ORDER_ID)).willReturn(false);

        assertErrorCode(() -> issue(CustomerInvoiceType.BALANCE), OrderErrorCode.INVOICE_BALANCE_BEFORE_COMPLETION);
    }

    @Test
    @DisplayName("잔금은 수주 금액에서 선금을 뺀 나머지다")
    void issueBalance_afterAdvance() {
        issue(CustomerInvoiceType.ADVANCE);
        given(partnerOrderRepository.existsCompletedBySalesOrderId(SALES_ORDER_ID)).willReturn(true);

        CustomerInvoiceDto.Response.SalesOrderBilling billing = issue(CustomerInvoiceType.BALANCE);

        CustomerInvoice balance = stored.get(1);
        assertThat(balance.getSupplyAmount()).isEqualByComparingTo("2100000");
        assertThat(balance.getVatAmount()).isEqualByComparingTo("210000");
        assertThat(billing.getBilledAmount()).isEqualByComparingTo("3300000");
        assertThat(billing.isBalanceIssuable()).isFalse();
    }

    @Test
    @DisplayName("선금 없이 잔금만 청구하면 전액이고, 그 뒤에는 선금을 청구할 수 없다")
    void issueBalance_withoutAdvance() {
        given(partnerOrderRepository.existsCompletedBySalesOrderId(SALES_ORDER_ID)).willReturn(true);

        issue(CustomerInvoiceType.BALANCE);

        assertThat(stored.getFirst().getTotalAmount()).isEqualByComparingTo("3300000");
        assertErrorCode(() -> issue(CustomerInvoiceType.ADVANCE), OrderErrorCode.INVOICE_ADVANCE_AFTER_BALANCE);
    }

    @Test
    @DisplayName("입금을 기록하면 PAID가 되고 미래 입금일은 받지 않는다")
    void recordPayment() {
        issue(CustomerInvoiceType.ADVANCE);
        CustomerInvoice advance = stored.getFirst();
        given(customerInvoiceRepository.findById(advance.getId())).willReturn(Optional.of(advance));
        LocalDate today = LocalDate.now(SalesOrderService.PLATFORM_ZONE);

        assertErrorCode(
                () -> customerInvoiceService.recordPayment(advance.getId(), ADMIN_ID, ROLE,
                        new CustomerInvoiceDto.Request.RecordPayment(today.plusDays(1), BigDecimal.TEN, null)),
                OrderErrorCode.INVOICE_PAID_DATE_INVALID
        );
        customerInvoiceService.recordPayment(advance.getId(), ADMIN_ID, ROLE,
                new CustomerInvoiceDto.Request.RecordPayment(today, BigDecimal.valueOf(990_000), " 계좌이체 "));

        assertThat(advance.getStatus()).isEqualTo(CustomerInvoiceStatus.PAID);
        assertThat(advance.getPaymentMemo()).isEqualTo("계좌이체");
        assertErrorCode(
                () -> customerInvoiceService.cancelInvoice(advance.getId(), ADMIN_ID, ROLE, "취소"),
                OrderErrorCode.INVOICE_NOT_ISSUED
        );
    }

    @Test
    @DisplayName("잔금 청구가 있으면 선금 청구를 취소할 수 없다")
    void cancelAdvance_rejectedWhileBalanceExists() {
        issue(CustomerInvoiceType.ADVANCE);
        given(partnerOrderRepository.existsCompletedBySalesOrderId(SALES_ORDER_ID)).willReturn(true);
        issue(CustomerInvoiceType.BALANCE);
        CustomerInvoice advance = stored.getFirst();
        given(customerInvoiceRepository.findById(advance.getId())).willReturn(Optional.of(advance));

        assertErrorCode(
                () -> customerInvoiceService.cancelInvoice(advance.getId(), ADMIN_ID, ROLE, "금액 오류"),
                OrderErrorCode.INVOICE_BALANCE_EXISTS
        );
    }

    @Test
    @DisplayName("세금계산서 발행일·승인번호를 기록한다")
    void recordTaxInvoice() {
        issue(CustomerInvoiceType.ADVANCE);
        CustomerInvoice advance = stored.getFirst();
        given(customerInvoiceRepository.findById(advance.getId())).willReturn(Optional.of(advance));

        customerInvoiceService.recordTaxInvoice(advance.getId(), ADMIN_ID, ROLE,
                new CustomerInvoiceDto.Request.RecordTaxInvoice(LocalDate.of(2026, 11, 12), " 20261112-41000000-12345678 "));

        assertThat(advance.getTaxInvoiceApprovalNumber()).isEqualTo("20261112-41000000-12345678");
    }

    @Test
    @DisplayName("ACTION_CUSTOMER_INVOICE_MANAGE 권한이 없으면 청구할 수 없다")
    void issue_requiresPermission() {
        assertErrorCode(
                () -> customerInvoiceService.issueInvoice(SALES_ORDER_ID, ADMIN_ID, "PLATFORM_SUPPORT",
                        new CustomerInvoiceDto.Request.Issue(CustomerInvoiceType.ADVANCE, LocalDate.now().plusDays(7), null, null)),
                CommonErrorCode.ACCESS_DENIED
        );
    }

    private CustomerInvoiceDto.Response.SalesOrderBilling issue(CustomerInvoiceType type) {
        return issueAdvance(type, null, null);
    }

    private CustomerInvoiceDto.Response.SalesOrderBilling issueAdvance(CustomerInvoiceType type, DiscountType amountType, String value) {
        return customerInvoiceService.issueInvoice(SALES_ORDER_ID, ADMIN_ID, ROLE, new CustomerInvoiceDto.Request.Issue(
                type, LocalDate.now(SalesOrderService.PLATFORM_ZONE).plusDays(7), amountType, value != null ? new BigDecimal(value) : null));
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Object errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(errorCode);
    }
}
