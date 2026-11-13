package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderAcceptanceChannel;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.order.repository.QuoteTemplateRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link SalesOrderService} 단위 테스트 — 고객 견적 링크 수락(D02)과 수주 취소 조건.
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    private static final String TOKEN = "token-100";
    private static final String ROLE = "PLATFORM_OPS";

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private SalesOrderLineRepository salesOrderLineRepository;
    @Mock
    private QuoteTemplateRepository quoteTemplateRepository;
    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private BillingCatalogQueryService billingCatalogQueryService;
    @Mock
    private OrderPricingService orderPricingService;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    @InjectMocks
    private SalesOrderService salesOrderService;

    private SalesOrder salesOrder;

    @BeforeEach
    void setUp() {
        salesOrder = OrderFixtures.salesOrder(100L, OrderFixtures.customer(1L), OrderFixtures.plan(1L));
    }

    @Test
    @DisplayName("파트너에게 보일 패키지 구성은 포함 수량이 있는 상품만 상품 표시 순서대로 준다")
    void findPlanInclusions_keepsIncludedProductsInDisplayOrder() {
        UnitProduct signers = OrderFixtures.unitProduct(11L, UnitProductType.SIGNERS, "서명자");
        signers.updateDisplayOrder(2);
        UnitProduct templates = OrderFixtures.unitProduct(12L, UnitProductType.TEMPLATES, "템플릿 문서");
        templates.updateDisplayOrder(1);
        UnitProduct tablets = OrderFixtures.unitProduct(13L, UnitProductType.TABLETS, "태블릿");
        BillingPlanUnitProduct signerRow = OrderFixtures.planUnitProduct(salesOrder.getBillingPlan(), signers, 300);
        BillingPlanUnitProduct templateRow = OrderFixtures.planUnitProduct(salesOrder.getBillingPlan(), templates, 2);
        BillingPlanUnitProduct tabletRow = OrderFixtures.planUnitProduct(salesOrder.getBillingPlan(), tablets, 0);
        given(billingCatalogQueryService.findPlanUnitProducts(1L)).willReturn(List.of(signerRow, tabletRow, templateRow));

        assertThat(salesOrderService.findPlanInclusions(salesOrder)).containsExactly(templateRow, signerRow);
    }

    @Test
    @DisplayName("고객이 견적 링크에서 수락하면 수락자와 경로를 남긴다")
    void acceptQuoteByAccessToken_acceptsQuotedOrder() {
        given(salesOrderRepository.findByQuoteAccessToken(TOKEN)).willReturn(Optional.of(salesOrder));
        given(salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(100L)).willReturn(List.of());

        salesOrderService.acceptQuoteByAccessToken(TOKEN, "김담당");

        assertThat(salesOrder.getStatus()).isEqualTo(SalesOrderStatus.ACCEPTED);
        assertThat(salesOrder.getAcceptedByName()).isEqualTo("김담당");
        assertThat(salesOrder.getAcceptanceChannel()).isEqualTo(SalesOrderAcceptanceChannel.CUSTOMER_LINK);
    }

    @Test
    @DisplayName("견적 유효기간이 지났으면 링크 수락을 거부한다")
    void acceptQuoteByAccessToken_rejectsExpiredQuote() {
        ReflectionTestUtils.setField(salesOrder, "quoteValidUntil", LocalDate.now().minusDays(2));
        given(salesOrderRepository.findByQuoteAccessToken(TOKEN)).willReturn(Optional.of(salesOrder));

        assertThatThrownBy(() -> salesOrderService.acceptQuoteByAccessToken(TOKEN, "김담당"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_QUOTE_EXPIRED);
        assertThat(salesOrder.getStatus()).isEqualTo(SalesOrderStatus.QUOTED);
    }

    @Test
    @DisplayName("취소된 수주의 링크는 찾을 수 없다")
    void retrieveQuoteByAccessToken_hidesCancelledOrder() {
        salesOrder.cancel("고객 취소");
        given(salesOrderRepository.findByQuoteAccessToken(TOKEN)).willReturn(Optional.of(salesOrder));

        assertThatThrownBy(() -> salesOrderService.retrieveQuoteByAccessToken(TOKEN))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("파트너가 수락한 발주가 있으면 수주를 취소하지 않는다")
    void cancelSalesOrder_rejectsWhenPartnerAccepted() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_SALES_ORDER_MANAGE")).willReturn(true);
        given(salesOrderRepository.findById(100L)).willReturn(Optional.of(salesOrder));
        PartnerOrder accepted = partnerOrder();
        accepted.accept(70L, null);
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(100L)).willReturn(List.of(accepted));

        assertThatThrownBy(() -> salesOrderService.cancelSalesOrder(100L, 1L, ROLE, "고객 취소"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_ORDER_HAS_ACCEPTED_PARTNER_ORDER);
    }

    @Test
    @DisplayName("취소되지 않은 청구가 있으면 수주를 취소하지 않는다")
    void cancelSalesOrder_rejectsWhenInvoiceActive() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_SALES_ORDER_MANAGE")).willReturn(true);
        given(salesOrderRepository.findById(100L)).willReturn(Optional.of(salesOrder));
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(100L)).willReturn(List.of());
        given(customerInvoiceRepository.existsBySalesOrderIdAndStatusNot(100L, CustomerInvoiceStatus.CANCELLED)).willReturn(true);

        assertThatThrownBy(() -> salesOrderService.cancelSalesOrder(100L, 1L, ROLE, "고객 취소"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_ORDER_HAS_ACTIVE_INVOICE);
    }

    @Test
    @DisplayName("수주를 취소하면 응답 대기 발주도 함께 취소한다")
    void cancelSalesOrder_cancelsIssuedPartnerOrders() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_SALES_ORDER_MANAGE")).willReturn(true);
        given(salesOrderRepository.findById(100L)).willReturn(Optional.of(salesOrder));
        PartnerOrder issued = partnerOrder();
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(100L)).willReturn(List.of(issued));
        given(salesOrderLineRepository.findAllBySalesOrderIdOrderByDisplayOrder(100L)).willReturn(List.of());

        salesOrderService.cancelSalesOrder(100L, 1L, ROLE, "고객 취소");

        assertThat(salesOrder.getStatus()).isEqualTo(SalesOrderStatus.CANCELLED);
        assertThat(issued.getStatus()).isEqualTo(PartnerOrderStatus.CANCELLED);
        assertThat(issued.getCancellationReason()).isEqualTo("고객 취소");
    }

    private PartnerOrder partnerOrder() {
        return PartnerOrder.builder()
                .salesOrder(salesOrder)
                .organization(OrderFixtures.organization(7L))
                .currencyCode("KRW")
                .baseFee(BigDecimal.ZERO)
                .tabletUnitRate(BigDecimal.ZERO)
                .onsiteStaffDayRate(BigDecimal.ZERO)
                .regionSurcharge(BigDecimal.ZERO)
                .adjustmentAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .responseDueAt(LocalDateTime.now().plusDays(1))
                .issuedBy(1L)
                .build();
    }
}
