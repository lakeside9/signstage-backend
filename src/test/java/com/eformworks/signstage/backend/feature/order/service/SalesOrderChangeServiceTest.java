package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.model.PlatformOrderCeremonyCommand;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminSalesOrderDto;
import com.eformworks.signstage.backend.feature.order.dto.SalesOrderChangeDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceType;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderAcceptanceChannel;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChange;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeLine;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLineType;
import com.eformworks.signstage.backend.feature.order.entity.ScopeChangeRequest;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCalculation;
import com.eformworks.signstage.backend.feature.order.model.ScopeExtras;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderChangeLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderChangeRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.ScopeChangeRequestRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link SalesOrderChangeService} 단위 테스트 — 수락된 수주의 범위 변경(D08). */
@ExtendWith(MockitoExtension.class)
class SalesOrderChangeServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long SALES_ORDER_ID = 100L;
    private static final String ROLE = "PLATFORM_OPS";

    @Mock
    private SalesOrderChangeRepository salesOrderChangeRepository;
    @Mock
    private SalesOrderChangeLineRepository salesOrderChangeLineRepository;
    @Mock
    private ScopeChangeRequestRepository scopeChangeRequestRepository;
    @Mock
    private SalesOrderLineRepository salesOrderLineRepository;
    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private OrderPricingService orderPricingService;
    @Mock
    private PlatformOrderCeremonyService platformOrderCeremonyService;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;
    @Mock
    private OrderNotificationService orderNotificationService;

    @InjectMocks
    private SalesOrderChangeService salesOrderChangeService;

    private SalesOrder salesOrder;
    private Organization partner;
    private UnitProduct tablets;

    @BeforeEach
    void setUp() {
        salesOrder = OrderFixtures.salesOrder(SALES_ORDER_ID, OrderFixtures.customer(1L), OrderFixtures.plan(1L));
        salesOrder.accept("고객 담당자", SalesOrderAcceptanceChannel.PLATFORM_RECORDED);
        partner = OrderFixtures.organization(7L);
        tablets = OrderFixtures.unitProduct(11L, UnitProductType.TABLETS, "태블릿");
    }

    @Test
    @DisplayName("응답 대기 중인 발주가 있으면 범위 변경을 제안하지 않는다")
    void proposeChange_rejectsPendingPartnerOrder() {
        givenManageAllowed();
        PartnerOrder pending = partnerOrder(LocalDateTime.now().plusDays(1));
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of(pending));

        assertThatThrownBy(() -> salesOrderChangeService.proposeChange(SALES_ORDER_ID, ADMIN_ID, ROLE, proposeRequest(6)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_ORDER_CHANGE_PARTNER_ORDER_PENDING);
    }

    @Test
    @DisplayName("잔금을 청구한 수주는 범위를 바꾸지 않는다")
    void proposeChange_rejectsBalanceInvoiced() {
        givenManageAllowed();
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of());
        CustomerInvoice balance = CustomerInvoice.builder()
                .salesOrder(salesOrder)
                .invoiceType(CustomerInvoiceType.BALANCE)
                .supplyAmount(BigDecimal.valueOf(3_000_000))
                .vatAmount(BigDecimal.valueOf(300_000))
                .issueDate(OrderFixtures.TODAY)
                .dueDate(OrderFixtures.TODAY.plusDays(14))
                .issuedBy(ADMIN_ID)
                .build();
        given(customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(SALES_ORDER_ID)).willReturn(List.of(balance));

        assertThatThrownBy(() -> salesOrderChangeService.proposeChange(SALES_ORDER_ID, ADMIN_ID, ROLE, proposeRequest(6)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_ORDER_CHANGE_BALANCE_INVOICED);
    }

    @Test
    @DisplayName("수락된 발주가 있으면 발주 단가 스냅샷으로 발주 금액을 다시 계산하고 마진을 검사하며 열린 요청을 닫는다")
    void proposeChange_recalculatesPartnerAmountFromSnapshot() {
        givenManageAllowed();
        PartnerOrder accepted = acceptedOrder();
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of(accepted));
        given(customerInvoiceRepository.findAllBySalesOrderIdOrderByIdAsc(SALES_ORDER_ID)).willReturn(List.of());
        given(orderPricingService.calculateSalesQuote(any(), anyList(), any(), any(), any(), any(), any()))
                .willReturn(calculation(6, BigDecimal.valueOf(3_400_000)));
        given(salesOrderChangeRepository.save(any(SalesOrderChange.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(salesOrderChangeLineRepository.save(any(SalesOrderChangeLine.class))).willAnswer(invocation -> invocation.getArgument(0));
        ScopeChangeRequest openRequest = ScopeChangeRequest.builder().partnerOrder(accepted).content("태블릿 추가 필요").requestedBy(70L).build();
        given(scopeChangeRequestRepository.findAllByPartnerOrderSalesOrderIdAndClosedAtIsNull(SALES_ORDER_ID))
                .willReturn(List.of(openRequest));

        SalesOrderChangeDto.Response.AdminChange change =
                salesOrderChangeService.proposeChange(SALES_ORDER_ID, ADMIN_ID, ROLE, proposeRequest(6));

        // 기본 500,000 + 태블릿 6 × 100,000 + 인력 1 × 400,000 = 1,500,000
        assertThat(change.getPartnerAmount()).isEqualByComparingTo("1500000");
        assertThat(change.getBeforePartnerAmount()).isEqualByComparingTo("1300000");
        assertThat(change.getSupplyAmount()).isEqualByComparingTo("3400000");
        assertThat(change.getStatus()).isEqualTo("PROPOSED");
        assertThat(change.isNeedsPartnerConsent()).isTrue();
        verify(orderPricingService).checkPartnerOrderMargin(BigDecimal.valueOf(3_400_000), new BigDecimal("1500000"));
        verify(orderPricingService, never()).estimatePartnerAmountWithinMargin(any(), any(), any(), any());
        assertThat(openRequest.isOpen()).isFalse();
        verify(orderNotificationService).scopeChangeProposed(any());
        verify(salesOrderChangeRepository).save(argThat(saved -> Long.valueOf(61L).equals(saved.getRegionSurchargeId())));
        verify(salesOrderChangeLineRepository).save(argThat(line -> Long.valueOf(51L).equals(line.getCustomerPriceId())));
    }

    @Test
    @DisplayName("고객 수락과 파트너 동의를 모두 받으면 수주·발주·행사에 반영하고, 늘어난 옵션만 행사에 더한다")
    void acceptByPartner_appliesWhenCustomerAccepted() {
        PartnerOrder accepted = acceptedOrder();
        SalesOrderChange change = proposedChange(accepted, 6);
        change.recordCustomerAcceptance("고객 담당자");
        given(salesOrderChangeRepository.findByIdAndPartnerOrderId(50L, 300L)).willReturn(Optional.of(change));
        given(salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(SALES_ORDER_ID))
                .willReturn(List.of(salesOrderLine(2)));
        given(salesOrderChangeLineRepository.findAllBySalesOrderChangeIdOrderByDisplayOrder(50L))
                .willReturn(List.of(changeLine(change, 4)));

        salesOrderChangeService.acceptByPartner(accepted, 50L, 70L);

        assertThat(change.getStatus()).isEqualTo(SalesOrderChangeStatus.APPLIED);
        assertThat(salesOrder.getSupplyAmount()).isEqualByComparingTo("3400000");
        assertThat(salesOrder.getTabletCount()).isEqualTo(6);
        assertThat(accepted.getTotalAmount()).isEqualByComparingTo("1500000");
        assertThat(accepted.getTabletCount()).isEqualTo(6);
        verify(salesOrderLineRepository).deleteAllBySalesOrderId(SALES_ORDER_ID);
        verify(salesOrderLineRepository).save(any(SalesOrderLine.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlatformOrderCeremonyCommand.ScopeOption>> additions = ArgumentCaptor.forClass(List.class);
        verify(platformOrderCeremonyService).applyScopeChange(eq(accepted.getCeremony()), any(), any(), any(), additions.capture());
        assertThat(additions.getValue()).containsExactly(new PlatformOrderCeremonyCommand.ScopeOption(11L, 2));
        verify(orderNotificationService).scopeChangeApplied(change);
        verify(orderNotificationService).scopeChangeResponded(change);
    }

    @Test
    @DisplayName("파트너가 먼저 동의하면 고객 수락 전까지 반영하지 않는다")
    void acceptByPartner_waitsForCustomer() {
        PartnerOrder accepted = acceptedOrder();
        SalesOrderChange change = proposedChange(accepted, 6);
        given(salesOrderChangeRepository.findByIdAndPartnerOrderId(50L, 300L)).willReturn(Optional.of(change));

        salesOrderChangeService.acceptByPartner(accepted, 50L, 70L);

        assertThat(change.getStatus()).isEqualTo(SalesOrderChangeStatus.PROPOSED);
        assertThat(change.isPartnerAccepted()).isTrue();
        assertThat(accepted.getTotalAmount()).isEqualByComparingTo("1300000");
        verify(salesOrderLineRepository, never()).deleteAllBySalesOrderId(any());
    }

    @Test
    @DisplayName("파트너가 거절하면 반영하지 않고, 다시 응답할 수 없다")
    void rejectByPartner_marksRejected() {
        PartnerOrder accepted = acceptedOrder();
        SalesOrderChange change = proposedChange(accepted, 6);
        given(salesOrderChangeRepository.findByIdAndPartnerOrderId(50L, 300L)).willReturn(Optional.of(change));

        salesOrderChangeService.rejectByPartner(accepted, 50L, 70L, "인력 부족");

        assertThat(change.getStatus()).isEqualTo(SalesOrderChangeStatus.REJECTED);
        assertThat(change.getPartnerRejectionReason()).isEqualTo("인력 부족");
        assertThatThrownBy(() -> salesOrderChangeService.acceptByPartner(accepted, 50L, 70L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_ORDER_CHANGE_NOT_PROPOSED);
    }

    private void givenManageAllowed() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_SALES_ORDER_MANAGE")).willReturn(true);
        given(salesOrderService.findSalesOrderOrThrow(SALES_ORDER_ID)).willReturn(salesOrder);
    }

    private SalesOrderChange proposedChange(PartnerOrder partnerOrder, int tabletCount) {
        SalesOrderChange change = SalesOrderChange.builder()
                .salesOrder(salesOrder)
                .partnerOrder(partnerOrder)
                .reason("태블릿 추가")
                .tabletCount(tabletCount)
                .onsiteStaffPersonDays(1)
                .supplyAmount(BigDecimal.valueOf(3_400_000))
                .vatAmount(BigDecimal.valueOf(340_000))
                .estimatedPartnerAmount(BigDecimal.valueOf(1_500_000))
                .partnerAmount(BigDecimal.valueOf(1_500_000))
                .location("서울")
                .startsAt(salesOrder.getStartsAt())
                .endsAt(salesOrder.getEndsAt())
                .proposedBy(ADMIN_ID)
                .build();
        ReflectionTestUtils.setField(change, "id", 50L);
        return change;
    }

    /** 단가 기본 500,000 · 태블릿 100,000 · 인력 400,000, 태블릿 4·인력 1 → 1,300,000. */
    private PartnerOrder acceptedOrder() {
        PartnerOrder order = PartnerOrder.builder()
                .salesOrder(salesOrder)
                .organization(partner)
                .currencyCode("KRW")
                .baseFee(BigDecimal.valueOf(500_000))
                .tabletCount(4)
                .tabletUnitRate(BigDecimal.valueOf(100_000))
                .onsiteStaffPersonDays(1)
                .onsiteStaffDayRate(BigDecimal.valueOf(400_000))
                .regionSurcharge(BigDecimal.ZERO)
                .adjustmentAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(1_300_000))
                .responseDueAt(LocalDateTime.now().plusDays(1))
                .issuedBy(ADMIN_ID)
                .build();
        ReflectionTestUtils.setField(order, "id", 300L);
        Ceremony ceremony = Ceremony.builder().organization(partner).title("협약식").build();
        ReflectionTestUtils.setField(ceremony, "id", 900L);
        order.accept(70L, ceremony);
        return order;
    }

    private PartnerOrder partnerOrder(LocalDateTime responseDueAt) {
        return PartnerOrder.builder()
                .salesOrder(salesOrder)
                .organization(partner)
                .currencyCode("KRW")
                .baseFee(BigDecimal.ZERO)
                .tabletUnitRate(BigDecimal.ZERO)
                .onsiteStaffDayRate(BigDecimal.ZERO)
                .regionSurcharge(BigDecimal.ZERO)
                .adjustmentAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .responseDueAt(responseDueAt)
                .issuedBy(ADMIN_ID)
                .build();
    }

    private SalesQuoteCalculation calculation(int tabletCount, BigDecimal supplyAmount) {
        SalesQuoteCalculation.Line line = new SalesQuoteCalculation.Line(
                SalesOrderLineType.UNIT_PRODUCT, tablets, "태블릿", tabletCount - 2, BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(100_000L * (tabletCount - 2)), "KR_VAT_STANDARD", BigDecimal.TEN, BigDecimal.ZERO, 51L
        );
        return new SalesQuoteCalculation(List.of(line), supplyAmount, supplyAmount.divide(BigDecimal.TEN), tabletCount, 1, ScopeExtras.NONE, 61L);
    }

    private SalesOrderLine salesOrderLine(int quantity) {
        return SalesOrderLine.builder()
                .salesOrder(salesOrder)
                .lineType(SalesOrderLineType.UNIT_PRODUCT)
                .unitProduct(tablets)
                .name("태블릿")
                .quantity(quantity)
                .unitPrice(BigDecimal.valueOf(100_000))
                .amount(BigDecimal.valueOf(100_000L * quantity))
                .taxCode("KR_VAT_STANDARD")
                .taxRatePercent(BigDecimal.TEN)
                .taxAmount(BigDecimal.ZERO)
                .build();
    }

    private SalesOrderChangeLine changeLine(SalesOrderChange change, int quantity) {
        return SalesOrderChangeLine.builder()
                .salesOrderChange(change)
                .lineType(SalesOrderLineType.UNIT_PRODUCT)
                .unitProduct(tablets)
                .name("태블릿")
                .quantity(quantity)
                .unitPrice(BigDecimal.valueOf(100_000))
                .amount(BigDecimal.valueOf(100_000L * quantity))
                .taxCode("KR_VAT_STANDARD")
                .taxRatePercent(BigDecimal.TEN)
                .taxAmount(BigDecimal.ZERO)
                .build();
    }

    private static SalesOrderChangeDto.Request.Propose proposeRequest(int tablets) {
        return new SalesOrderChangeDto.Request.Propose(
                "고객 요청으로 태블릿 추가",
                List.of(new PlatformAdminSalesOrderDto.Request.Option(11L, tablets - 2)),
                null, null, null, null, null, null, null, null, null
        );
    }
}
