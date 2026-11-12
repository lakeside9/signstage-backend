package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminPartnerOrderDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderAcceptanceChannel;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderAmount;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.service.PartnerSubcontractProfileService;
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

/**
 * {@link PartnerOrderService} 단위 테스트 — 발주 발급 조건(D04·D05·D22)과 금액 스냅샷.
 */
@ExtendWith(MockitoExtension.class)
class PartnerOrderServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final String ROLE = "PLATFORM_OPS";
    private static final Long SALES_ORDER_ID = 100L;
    private static final Long PARTNER_ID = 7L;

    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private PartnerSubcontractProfileService partnerSubcontractProfileService;
    @Mock
    private OrderPricingService orderPricingService;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;
    @Mock
    private PlatformOrderCeremonyService platformOrderCeremonyService;
    @Mock
    private SalesOrderChangeService salesOrderChangeService;
    @Mock
    private OrderNotificationService orderNotificationService;

    @InjectMocks
    private PartnerOrderService partnerOrderService;

    private SalesOrder salesOrder;
    private Organization partner;

    @BeforeEach
    void setUp() {
        salesOrder = OrderFixtures.salesOrder(SALES_ORDER_ID, OrderFixtures.customer(1L), OrderFixtures.plan(1L));
        partner = OrderFixtures.organization(PARTNER_ID);
        given(rolePermissionService.isAllowed(ROLE, "ACTION_PARTNER_ORDER_ISSUE")).willReturn(true);
        lenient().when(salesOrderService.findSalesOrderOrThrow(SALES_ORDER_ID)).thenReturn(salesOrder);
    }

    @Test
    @DisplayName("고객이 수락하지 않은 수주에는 발주하지 않는다")
    void issuePartnerOrder_rejectsQuotedSalesOrder() {
        assertThatThrownBy(() -> partnerOrderService.issuePartnerOrder(SALES_ORDER_ID, ADMIN_ID, ROLE, issueRequest(null, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_ORDER_NOT_ACCEPTED);
    }

    @Test
    @DisplayName("발주 금액을 조정하면 사유가 필요하다")
    void issuePartnerOrder_requiresAdjustmentReason() {
        salesOrder.accept("고객 담당자", SalesOrderAcceptanceChannel.PLATFORM_RECORDED);

        assertThatThrownBy(() -> partnerOrderService.issuePartnerOrder(
                SALES_ORDER_ID, ADMIN_ID, ROLE, issueRequest(BigDecimal.valueOf(-10_000), " ")
        )).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PARTNER_ADJUSTMENT_REASON_REQUIRED);
    }

    @Test
    @DisplayName("응답 대기 중인 발주가 있으면 새 발주를 거부한다(분할 발주 없음)")
    void issuePartnerOrder_rejectsWhenActiveOrderExists() {
        salesOrder.accept("고객 담당자", SalesOrderAcceptanceChannel.PLATFORM_RECORDED);
        PartnerOrder pending = partnerOrder(LocalDateTime.now().plusDays(1));
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of(pending));

        assertThatThrownBy(() -> partnerOrderService.issuePartnerOrder(SALES_ORDER_ID, ADMIN_ID, ROLE, issueRequest(null, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PARTNER_ORDER_ALREADY_ACTIVE);
    }

    @Test
    @DisplayName("외주 정보 요건을 채우지 않은 파트너에게는 발주하지 않는다")
    void issuePartnerOrder_rejectsIneligiblePartner() {
        salesOrder.accept("고객 담당자", SalesOrderAcceptanceChannel.PLATFORM_RECORDED);
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of());
        given(organizationRepository.findById(PARTNER_ID)).willReturn(Optional.of(partner));
        given(partnerSubcontractProfileService.isOrderEligible(PARTNER_ID)).willReturn(false);

        assertThatThrownBy(() -> partnerOrderService.issuePartnerOrder(SALES_ORDER_ID, ADMIN_ID, ROLE, issueRequest(null, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PARTNER_NOT_ELIGIBLE);
        verify(partnerOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("기한이 지난 이전 발주는 EXPIRED로 확정하고, 파트너 단가표 금액을 스냅샷해 발급한다")
    void issuePartnerOrder_expiresOverdueAndSnapshotsAmount() {
        salesOrder.accept("고객 담당자", SalesOrderAcceptanceChannel.PLATFORM_RECORDED);
        PartnerOrder overdue = partnerOrder(LocalDateTime.now().minusHours(1));
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(SALES_ORDER_ID)).willReturn(List.of(overdue));
        given(organizationRepository.findById(PARTNER_ID)).willReturn(Optional.of(partner));
        given(partnerSubcontractProfileService.isOrderEligible(PARTNER_ID)).willReturn(true);
        PartnerOrderAmount amount = new PartnerOrderAmount(
                BigDecimal.valueOf(500_000), 4, BigDecimal.valueOf(100_000), 1, BigDecimal.valueOf(400_000),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(1_300_000), 0, 0, 0, BigDecimal.ZERO, 31L, 41L, 0, BigDecimal.ZERO
        );
        given(orderPricingService.calculatePartnerOrderAmount(eq(PARTNER_ID), anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .willReturn(amount);
        given(partnerOrderRepository.save(any(PartnerOrder.class))).willAnswer(invocation -> invocation.getArgument(0));

        partnerOrderService.issuePartnerOrder(SALES_ORDER_ID, ADMIN_ID, ROLE, issueRequest(null, null));

        assertThat(overdue.getStatus()).isEqualTo(PartnerOrderStatus.EXPIRED);
        verify(orderPricingService).checkPartnerOrderMargin(BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(1_300_000));
        ArgumentCaptor<PartnerOrder> captor = ArgumentCaptor.forClass(PartnerOrder.class);
        verify(partnerOrderRepository).save(captor.capture());
        PartnerOrder issued = captor.getValue();
        assertThat(issued.getStatus()).isEqualTo(PartnerOrderStatus.ISSUED);
        assertThat(issued.getTotalAmount()).isEqualByComparingTo("1300000");
        assertThat(issued.getTabletCount()).isEqualTo(4);
        assertThat(issued.getIssuedBy()).isEqualTo(ADMIN_ID);
        assertThat(issued.getPartnerRateCardId()).isEqualTo(31L);
        assertThat(issued.getRegionSurchargeId()).isEqualTo(41L);
    }

    @Test
    @DisplayName("진행 중인 범위 변경이 있으면 새 발주를 내지 않는다(D08)")
    void issuePartnerOrder_rejectsDuringScopeChange() {
        salesOrder.accept("고객 담당자", SalesOrderAcceptanceChannel.PLATFORM_RECORDED);
        given(salesOrderChangeService.hasProposedChange(SALES_ORDER_ID)).willReturn(true);

        assertThatThrownBy(() -> partnerOrderService.issuePartnerOrder(SALES_ORDER_ID, ADMIN_ID, ROLE, issueRequest(null, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.SALES_ORDER_CHANGE_IN_PROGRESS);
    }

    @Test
    @DisplayName("수락한 발주 취소(파트너 교체)는 교체 권한이 있어야 한다")
    void cancelPartnerOrder_acceptedRequiresReplacePermission() {
        PartnerOrder accepted = acceptedOrder(false);
        given(partnerOrderRepository.findById(300L)).willReturn(Optional.of(accepted));

        assertThatThrownBy(() -> partnerOrderService.cancelPartnerOrder(300L, ADMIN_ID, ROLE, "파트너 사정"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
        assertThat(accepted.getStatus()).isEqualTo(PartnerOrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("행사가 완료된 발주는 수락 후 취소할 수 없다")
    void cancelPartnerOrder_rejectsCompletedCeremony() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_PARTNER_ORDER_REPLACE")).willReturn(true);
        given(partnerOrderRepository.findById(300L)).willReturn(Optional.of(acceptedOrder(true)));

        assertThatThrownBy(() -> partnerOrderService.cancelPartnerOrder(300L, ADMIN_ID, ROLE, "파트너 사정"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PARTNER_ORDER_CEREMONY_COMPLETED);
    }

    @Test
    @DisplayName("수락 후 취소하면 행사 연결은 남기고 담당자 배정·진행 중 범위 변경을 정리하고 파트너에게 알린다")
    void cancelPartnerOrder_acceptedReleasesCeremony() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_PARTNER_ORDER_REPLACE")).willReturn(true);
        PartnerOrder accepted = acceptedOrder(false);
        given(partnerOrderRepository.findById(300L)).willReturn(Optional.of(accepted));

        partnerOrderService.cancelPartnerOrder(300L, ADMIN_ID, ROLE, "파트너 사정");

        assertThat(accepted.getStatus()).isEqualTo(PartnerOrderStatus.CANCELLED);
        assertThat(accepted.getCeremony()).isNotNull();
        verify(platformOrderCeremonyService).suspendPartnerAccess(accepted.getCeremony());
        verify(salesOrderChangeService).cancelProposedForPartnerOrder(eq(300L), any());
        verify(orderNotificationService).partnerOrderCancelled(accepted, "파트너 사정");
    }

    private PartnerOrder acceptedOrder(boolean ceremonyCompleted) {
        PartnerOrder order = partnerOrder(LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(order, "id", 300L);
        Ceremony ceremony = Ceremony.builder().organization(partner).title("협약식").build();
        ReflectionTestUtils.setField(ceremony, "id", 900L);
        if (ceremonyCompleted) {
            ceremony.changeStatus(CeremonyStatus.COMPLETED);
        }
        order.accept(70L, ceremony);
        return order;
    }

    private PartnerOrder partnerOrder(LocalDateTime responseDueAt) {
        return PartnerOrder.builder()
                .salesOrder(salesOrder)
                .organization(OrderFixtures.organization(8L))
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

    private static PlatformAdminPartnerOrderDto.Request.IssuePartnerOrder issueRequest(BigDecimal adjustment, String reason) {
        return new PlatformAdminPartnerOrderDto.Request.IssuePartnerOrder(
                PARTNER_ID, LocalDateTime.now().plusDays(2), adjustment, reason
        );
    }
}
