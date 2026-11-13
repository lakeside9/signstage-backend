package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.CustomerCancellationDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceType;
import com.eformworks.signstage.backend.feature.order.entity.CustomerRefund;
import com.eformworks.signstage.backend.feature.order.entity.PartnerCompensation;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderAcceptanceChannel;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.CustomerRefundRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerCompensationRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link CustomerCancellationService} 단위 테스트 — 고객 취소 수수료·환불·파트너 보상(C01~C04). */
@ExtendWith(MockitoExtension.class)
class CustomerCancellationServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long SALES_ORDER_ID = 100L;
    private static final String ROLE = "PLATFORM_SUPER";

    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock
    private CustomerRefundRepository customerRefundRepository;
    @Mock
    private PartnerCompensationRepository partnerCompensationRepository;
    @Mock
    private PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    @Mock
    private SalesOrderChangeService salesOrderChangeService;
    @Mock
    private PlatformOrderCeremonyService platformOrderCeremonyService;
    @Mock
    private OrderNotificationService orderNotificationService;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    private CustomerCancellationService customerCancellationService;
    private SalesOrder salesOrder;

    @BeforeEach
    void setUp() {
        customerCancellationService = new CustomerCancellationService(
                salesOrderService, partnerOrderRepository, customerInvoiceRepository, customerRefundRepository,
                partnerCompensationRepository, partnerLedgerEntryRepository, salesOrderChangeService, platformOrderCeremonyService,
                orderNotificationService, new CustomerCancellationPolicy("30:0,14:30,7:50,0:100", "14:0,7:20,0:50"),
                new MoneyCalculator(), rolePermissionService, auditLogRecorder
        );
        // 공급가 3,000,000 · 부가세 300,000
        salesOrder = OrderFixtures.salesOrder(SALES_ORDER_ID, OrderFixtures.customer(1L), OrderFixtures.plan(1L));
        salesOrder.accept("김담당", SalesOrderAcceptanceChannel.PLATFORM_RECORDED);
        lenient().when(rolePermissionService.isAllowed(eq(ROLE), any())).thenReturn(true);
        lenient().when(salesOrderService.findSalesOrderOrThrow(SALES_ORDER_ID)).thenReturn(salesOrder);
    }

    @Test
    @DisplayName("행사 10일 전 취소 — 수수료 50%가 받은 선금보다 많으면 차액을 취소 수수료 청구로 내고, 수락 파트너에게 20%를 보상한다")
    void cancelByCustomer_issuesFeeInvoiceAndCompensatesPartner() {
        eventInDays(10);
        CustomerInvoice paidAdvance = invoice(CustomerInvoiceType.ADVANCE, 1_000_000, 100_000);
        paidAdvance.recordPayment(ADMIN_ID, LocalDate.now(), BigDecimal.valueOf(1_100_000), null);
        given(customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(SALES_ORDER_ID)).willReturn(List.of(paidAdvance));
        Ceremony ceremony = Ceremony.builder().organization(OrderFixtures.organization(7L)).title("협약식").build();
        ReflectionTestUtils.setField(ceremony, "id", 900L);
        PartnerOrder accepted = OrderFixtures.partnerOrder(300L, OrderFixtures.organization(7L), 1_000_000);
        accepted.accept(70L, ceremony);
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of(accepted));
        given(partnerCompensationRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        customerCancellationService.cancelByCustomer(SALES_ORDER_ID, ADMIN_ID, ROLE, " 행사 취소 ");

        // 수수료 3,000,000 × 50% = 1,500,000, 받은 금액 1,100,000 → 추가 청구 400,000(부가세 없음)
        assertThat(salesOrder.getStatus()).isEqualTo(SalesOrderStatus.CANCELLED);
        assertThat(salesOrder.isCancelledByCustomer()).isTrue();
        assertThat(salesOrder.getCancellationFeeAmount()).isEqualByComparingTo("1500000");
        ArgumentCaptor<CustomerInvoice> invoiceCaptor = ArgumentCaptor.forClass(CustomerInvoice.class);
        verify(customerInvoiceRepository).save(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue().getInvoiceType()).isEqualTo(CustomerInvoiceType.CANCELLATION_FEE);
        assertThat(invoiceCaptor.getValue().getSupplyAmount()).isEqualByComparingTo("400000");
        assertThat(invoiceCaptor.getValue().getVatAmount()).isEqualByComparingTo("0");
        verify(customerRefundRepository, never()).save(any());

        // 보상 1,000,000 × 20% = 200,000, 원장에도 남긴다
        assertThat(accepted.getStatus()).isEqualTo(PartnerOrderStatus.CANCELLED);
        ArgumentCaptor<PartnerCompensation> compensationCaptor = ArgumentCaptor.forClass(PartnerCompensation.class);
        verify(partnerCompensationRepository).save(compensationCaptor.capture());
        assertThat(compensationCaptor.getValue().getAmount()).isEqualByComparingTo("200000");
        verify(partnerLedgerEntryRepository).save(any());
        verify(platformOrderCeremonyService).cancelForCustomer(ceremony);
        verify(salesOrderChangeService).cancelProposedForSalesOrder(eq(SALES_ORDER_ID), any());
    }

    @Test
    @DisplayName("행사 30일 이상 전 취소 — 수수료 0%라 받은 금액 전부를 환불 대기로 남기고 입금 전 청구는 취소한다")
    void cancelByCustomer_recordsRefundAndCancelsUnpaidInvoice() {
        eventInDays(40);
        CustomerInvoice paidAdvance = invoice(CustomerInvoiceType.ADVANCE, 500_000, 50_000);
        paidAdvance.recordPayment(ADMIN_ID, LocalDate.now(), BigDecimal.valueOf(550_000), null);
        CustomerInvoice unpaidAdvance = invoice(CustomerInvoiceType.ADVANCE, 400_000, 40_000);
        given(customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(SALES_ORDER_ID))
                .willReturn(List.of(paidAdvance, unpaidAdvance));
        PartnerOrder pending = OrderFixtures.partnerOrder(301L, OrderFixtures.organization(8L), 1_000_000);
        ReflectionTestUtils.setField(pending, "responseDueAt", LocalDateTime.now().plusDays(1));
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of(pending));

        customerCancellationService.cancelByCustomer(SALES_ORDER_ID, ADMIN_ID, ROLE, "일정 취소");

        ArgumentCaptor<CustomerRefund> refundCaptor = ArgumentCaptor.forClass(CustomerRefund.class);
        verify(customerRefundRepository).save(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getAmount()).isEqualByComparingTo("550000");
        assertThat(unpaidAdvance.getStatus()).isEqualTo(CustomerInvoiceStatus.CANCELLED);
        assertThat(paidAdvance.getStatus()).isEqualTo(CustomerInvoiceStatus.PAID);
        verify(customerInvoiceRepository, never()).save(any());
        // 응답 대기 발주는 취소만 하고 보상하지 않는다
        assertThat(pending.getStatus()).isEqualTo(PartnerOrderStatus.CANCELLED);
        verify(partnerCompensationRepository, never()).save(any());
    }

    @Test
    @DisplayName("미리보기는 저장하지 않고 수수료·환불·보상 계산만 보여준다")
    void preview_showsAmountsWithoutChanges() {
        eventInDays(20);
        CustomerInvoice paidAdvance = invoice(CustomerInvoiceType.ADVANCE, 1_000_000, 100_000);
        paidAdvance.recordPayment(ADMIN_ID, LocalDate.now(), BigDecimal.valueOf(1_100_000), null);
        given(customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(SALES_ORDER_ID)).willReturn(List.of(paidAdvance));
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of());

        CustomerCancellationDto.Response.Preview preview = customerCancellationService.preview(SALES_ORDER_ID, ROLE);

        assertThat(preview.getDaysBeforeEvent()).isEqualTo(20L);
        assertThat(preview.getFeeRatePercent()).isEqualByComparingTo("30");
        assertThat(preview.getFeeAmount()).isEqualByComparingTo("900000");
        assertThat(preview.getRefundAmount()).isEqualByComparingTo("200000");
        assertThat(preview.getAdditionalFeeAmount()).isEqualByComparingTo("0");
        assertThat(salesOrder.getStatus()).isEqualTo(SalesOrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("잔금을 청구했으면 고객 취소할 수 없다")
    void cancelByCustomer_rejectsAfterBalanceInvoice() {
        given(customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(SALES_ORDER_ID))
                .willReturn(List.of(invoice(CustomerInvoiceType.BALANCE, 2_000_000, 200_000)));

        assertErrorCode(() -> customerCancellationService.cancelByCustomer(SALES_ORDER_ID, ADMIN_ID, ROLE, "취소"),
                OrderErrorCode.CUSTOMER_CANCEL_BALANCE_ISSUED);
        assertThat(salesOrder.getStatus()).isEqualTo(SalesOrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("행사를 완료한 발주가 있으면 고객 취소할 수 없다")
    void cancelByCustomer_rejectsCompletedEvent() {
        given(partnerOrderRepository.existsCompletedBySalesOrderId(SALES_ORDER_ID)).willReturn(true);

        assertErrorCode(() -> customerCancellationService.cancelByCustomer(SALES_ORDER_ID, ADMIN_ID, ROLE, "취소"),
                OrderErrorCode.CUSTOMER_CANCEL_EVENT_COMPLETED);
    }

    @Test
    @DisplayName("수락한 발주가 있으면 파트너 교체 권한이 없을 때 거부한다")
    void cancelByCustomer_acceptedOrderRequiresReplacePermission() {
        eventInDays(40);
        given(customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(SALES_ORDER_ID)).willReturn(List.of());
        PartnerOrder accepted = OrderFixtures.partnerOrder(300L, OrderFixtures.organization(7L), 1_000_000);
        accepted.accept(70L, null);
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of(accepted));
        given(rolePermissionService.isAllowed(ROLE, "ACTION_PARTNER_ORDER_REPLACE")).willReturn(false);

        assertErrorCode(() -> customerCancellationService.cancelByCustomer(SALES_ORDER_ID, ADMIN_ID, ROLE, "취소"),
                CommonErrorCode.ACCESS_DENIED);
        assertThat(accepted.getStatus()).isEqualTo(PartnerOrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("견적 단계 수주는 고객 취소 대상이 아니다")
    void cancelByCustomer_rejectsQuotedOrder() {
        SalesOrder quoted = OrderFixtures.salesOrder(101L, OrderFixtures.customer(1L), OrderFixtures.plan(1L));
        given(salesOrderService.findSalesOrderOrThrow(101L)).willReturn(quoted);

        assertErrorCode(() -> customerCancellationService.cancelByCustomer(101L, ADMIN_ID, ROLE, "취소"),
                OrderErrorCode.SALES_ORDER_NOT_ACCEPTED);
    }

    private void eventInDays(long days) {
        ReflectionTestUtils.setField(salesOrder, "startsAt",
                LocalDateTime.now(SalesOrderService.PLATFORM_ZONE).plusDays(days).withHour(10));
    }

    private CustomerInvoice invoice(CustomerInvoiceType type, long supply, long vat) {
        return CustomerInvoice.builder()
                .salesOrder(salesOrder)
                .invoiceType(type)
                .supplyAmount(BigDecimal.valueOf(supply))
                .vatAmount(BigDecimal.valueOf(vat))
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(7))
                .issuedBy(ADMIN_ID)
                .build();
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Object errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(errorCode);
    }
}
