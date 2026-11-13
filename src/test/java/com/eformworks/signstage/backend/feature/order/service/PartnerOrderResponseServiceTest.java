package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.model.PlatformOrderCeremonyCommand;
import com.eformworks.signstage.backend.feature.ceremony.service.PlatformOrderCeremonyService;
import com.eformworks.signstage.backend.feature.order.dto.PartnerOrderDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLineType;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
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
 * {@link PartnerOrderResponseService} 단위 테스트 — 파트너의 발주 수락(행사 생성, D03 안 A)과
 * 응답 권한·기한.
 */
@ExtendWith(MockitoExtension.class)
class PartnerOrderResponseServiceTest {

    private static final Long PARTNER_ID = 7L;
    private static final Long USER_ID = 70L;
    private static final Long PARTNER_ORDER_ID = 300L;

    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private PlatformOrderCeremonyService platformOrderCeremonyService;
    @Mock
    private OrderInspectionService orderInspectionService;
    @Mock
    private SalesOrderChangeService salesOrderChangeService;
    @Mock
    private OrderNotificationService orderNotificationService;

    @InjectMocks
    private PartnerOrderResponseService partnerOrderResponseService;

    private Organization partner;
    private SalesOrder salesOrder;

    @BeforeEach
    void setUp() {
        partner = OrderFixtures.organization(PARTNER_ID);
        salesOrder = OrderFixtures.salesOrder(100L, OrderFixtures.customer(1L), OrderFixtures.plan(1L));
    }

    @Test
    @DisplayName("OPERATOR는 발주를 조회·응답할 수 없다")
    void acceptOrder_rejectsOperator() {
        givenMember(MemberRole.OPERATOR, false);

        assertThatThrownBy(() -> partnerOrderResponseService.acceptOrder(PARTNER_ID, PARTNER_ORDER_ID, USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("응답 기한이 지난 발주는 수락할 수 없다")
    void acceptOrder_rejectsOverdueOrder() {
        givenMember(MemberRole.OWNER, true);
        PartnerOrder overdue = partnerOrder(LocalDateTime.now().minusMinutes(1));
        given(partnerOrderRepository.findByIdAndOrganizationId(PARTNER_ORDER_ID, PARTNER_ID)).willReturn(Optional.of(overdue));

        assertThatThrownBy(() -> partnerOrderResponseService.acceptOrder(PARTNER_ID, PARTNER_ORDER_ID, USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.PARTNER_ORDER_EXPIRED);
        verify(platformOrderCeremonyService, never()).createPlatformOrderCeremony(any());
    }

    @Test
    @DisplayName("수락하면 수주 범위·일정·고객 담당자로 플랫폼 수주 행사를 만들고 발주에 연결한다")
    void acceptOrder_createsPlatformOrderCeremony() {
        givenMember(MemberRole.ADMIN, true);
        PartnerOrder issued = partnerOrder(LocalDateTime.now().plusDays(1));
        given(partnerOrderRepository.findByIdAndOrganizationId(PARTNER_ORDER_ID, PARTNER_ID)).willReturn(Optional.of(issued));
        SalesOrderLine tabletOption = SalesOrderLine.builder()
                .salesOrder(salesOrder)
                .lineType(SalesOrderLineType.UNIT_PRODUCT)
                .unitProduct(OrderFixtures.unitProduct(11L, UnitProductType.TABLETS, "태블릿"))
                .name("태블릿")
                .quantity(2)
                .unitPrice(BigDecimal.ONE)
                .amount(BigDecimal.TWO)
                .taxCode("KR_VAT_STANDARD")
                .taxRatePercent(BigDecimal.TEN)
                .taxAmount(BigDecimal.ZERO)
                .build();
        given(salesOrderService.findOptionLines(100L)).willReturn(List.of(tabletOption));
        Ceremony ceremony = Ceremony.builder().organization(partner).title("협약식").build();
        ReflectionTestUtils.setField(ceremony, "id", 900L);
        given(platformOrderCeremonyService.createPlatformOrderCeremony(any())).willReturn(ceremony);

        partnerOrderResponseService.acceptOrder(PARTNER_ID, PARTNER_ORDER_ID, USER_ID);

        ArgumentCaptor<PlatformOrderCeremonyCommand> captor = ArgumentCaptor.forClass(PlatformOrderCeremonyCommand.class);
        verify(platformOrderCeremonyService).createPlatformOrderCeremony(captor.capture());
        PlatformOrderCeremonyCommand command = captor.getValue();
        assertThat(command.organizationId()).isEqualTo(PARTNER_ID);
        assertThat(command.acceptingUserId()).isEqualTo(USER_ID);
        assertThat(command.billingPlanId()).isEqualTo(1L);
        assertThat(command.title()).isEqualTo("협약식");
        assertThat(command.organizingInstitution()).isEqualTo("고객사");
        assertThat(command.contactName()).isEqualTo("홍길동");
        assertThat(command.options()).containsExactly(new PlatformOrderCeremonyCommand.ScopeOption(11L, 2, "KR_VAT_STANDARD"));
        assertThat(issued.getStatus()).isEqualTo(PartnerOrderStatus.ACCEPTED);
        assertThat(issued.getCeremony()).isSameAs(ceremony);
        assertThat(issued.getRespondedBy()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("수락 후 취소된 이전 발주의 행사가 있으면 새로 만들지 않고 넘겨받는다(파트너 교체)")
    void acceptOrder_takesOverCarriedCeremony() {
        givenMember(MemberRole.ADMIN, true);
        PartnerOrder issued = partnerOrder(LocalDateTime.now().plusDays(1));
        given(partnerOrderRepository.findByIdAndOrganizationId(PARTNER_ORDER_ID, PARTNER_ID)).willReturn(Optional.of(issued));
        Ceremony ceremony = Ceremony.builder().organization(OrderFixtures.organization(8L)).title("협약식").build();
        ReflectionTestUtils.setField(ceremony, "id", 900L);
        PartnerOrder previous = partnerOrder(LocalDateTime.now().minusDays(3));
        previous.accept(80L, ceremony);
        previous.cancel("파트너 사정");
        given(partnerOrderRepository.findAllBySalesOrderIdOrderByIssuedAtDesc(100L)).willReturn(List.of(issued, previous));
        given(platformOrderCeremonyService.transferPlatformOrderCeremony(ceremony, PARTNER_ID, USER_ID)).willReturn(ceremony);
        given(salesOrderService.findPartnerScopeLines(100L)).willReturn(List.of());

        partnerOrderResponseService.acceptOrder(PARTNER_ID, PARTNER_ORDER_ID, USER_ID);

        verify(platformOrderCeremonyService, never()).createPlatformOrderCeremony(any());
        assertThat(issued.getStatus()).isEqualTo(PartnerOrderStatus.ACCEPTED);
        assertThat(issued.getCeremony()).isSameAs(ceremony);
        verify(orderNotificationService).partnerOrderResponded(issued);
    }

    @Test
    @DisplayName("발주 상세는 패키지 포함 구성을 상품별 실제 개수와 단위로 보여준다")
    void retrieveReceivedOrder_showsPackageItems() {
        givenMember(MemberRole.OWNER, true);
        PartnerOrder issued = partnerOrder(LocalDateTime.now().plusDays(1));
        given(partnerOrderRepository.findByIdAndOrganizationId(PARTNER_ORDER_ID, PARTNER_ID)).willReturn(Optional.of(issued));
        UnitProduct templates = OrderFixtures.unitProduct(12L, UnitProductType.TEMPLATES, "템플릿 문서");
        ReflectionTestUtils.setField(templates, "saleUnitQuantity", 10);
        given(salesOrderService.findPlanInclusions(salesOrder)).willReturn(List.of(
                OrderFixtures.planUnitProduct(salesOrder.getBillingPlan(), OrderFixtures.unitProduct(11L, UnitProductType.SIGNERS, "서명자"), 300),
                OrderFixtures.planUnitProduct(salesOrder.getBillingPlan(), templates, 2)
        ));

        PartnerOrderDto.Response.PartnerOrderDetail detail =
                partnerOrderResponseService.retrieveReceivedOrder(PARTNER_ID, PARTNER_ORDER_ID, USER_ID);

        assertThat(detail.getPackageItems())
                .extracting(PartnerOrderDto.Response.PackageItem::getName, PartnerOrderDto.Response.PackageItem::getQuantity,
                        PartnerOrderDto.Response.PackageItem::getQuantityUnit)
                .containsExactly(tuple("서명자", 300, "명"), tuple("템플릿 문서", 20, "개"));
    }

    @Test
    @DisplayName("거절하면 사유를 남기고 행사를 만들지 않는다")
    void rejectOrder_recordsReason() {
        givenMember(MemberRole.OWNER, true);
        PartnerOrder issued = partnerOrder(LocalDateTime.now().plusDays(1));
        given(partnerOrderRepository.findByIdAndOrganizationId(PARTNER_ORDER_ID, PARTNER_ID)).willReturn(Optional.of(issued));
        given(salesOrderService.findPartnerScopeLines(100L)).willReturn(List.of());

        partnerOrderResponseService.rejectOrder(PARTNER_ID, PARTNER_ORDER_ID, USER_ID, "일정 불가");

        assertThat(issued.getStatus()).isEqualTo(PartnerOrderStatus.REJECTED);
        assertThat(issued.getRejectionReason()).isEqualTo("일정 불가");
        verify(platformOrderCeremonyService, never()).createPlatformOrderCeremony(any());
    }

    private void givenMember(MemberRole role, boolean allowed) {
        Member member = Member.builder().organization(partner).role(role).status(MemberStatus.ACTIVE).build();
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(PARTNER_ID, USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(rolePermissionService.isAllowed(role.name(), "ACTION_PARTNER_ORDER_RESPOND")).willReturn(allowed);
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
                .totalAmount(BigDecimal.valueOf(1_300_000))
                .responseDueAt(responseDueAt)
                .issuedBy(1L)
                .build();
    }
}
