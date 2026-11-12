package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.order.dto.OrderInspectionDto;
import com.eformworks.signstage.backend.feature.order.entity.InspectionApprovalType;
import com.eformworks.signstage.backend.feature.order.entity.InspectionStatus;
import com.eformworks.signstage.backend.feature.order.entity.OrderInspection;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntryType;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.OrderInspectionRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link OrderInspectionService} 단위 테스트 — 검수 생성·승인 간주·감액 승인(D11·D13). */
@ExtendWith(MockitoExtension.class)
class OrderInspectionServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long INSPECTION_ID = 500L;
    private static final String ROLE = "PLATFORM_OPS";

    @Mock
    private OrderInspectionRepository orderInspectionRepository;
    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    private OrderInspectionService orderInspectionService;
    private Organization partner;

    @BeforeEach
    void setUp() {
        orderInspectionService = new OrderInspectionService(
                orderInspectionRepository, partnerOrderRepository, partnerLedgerEntryRepository,
                new PartnerSettlementPolicy(7, 60), rolePermissionService, auditLogRecorder
        );
        partner = OrderFixtures.organization(7L);
    }

    @Test
    @DisplayName("완료된 행사의 발주에 검수를 만들고, 기한이 지난 대기 검수는 감액 없이 승인 간주한다")
    void syncInspections_createsAndDeems() {
        PartnerOrder completedOrder = OrderFixtures.partnerOrder(301L, partner, 1_500_000);
        Ceremony ceremony = Ceremony.builder().organization(partner).title("협약식").build();
        ceremony.changeStatus(CeremonyStatus.COMPLETED);
        completedOrder.accept(70L, ceremony);
        given(partnerOrderRepository.findAcceptedCompletedWithoutInspection()).willReturn(List.of(completedOrder));

        OrderInspection overdue = OrderFixtures.inspection(302L, partner, 1_000_000, LocalDate.now().minusDays(20));
        given(orderInspectionRepository.findPendingDueBefore(any())).willReturn(List.of(overdue));

        int changed = orderInspectionService.syncInspections();

        assertThat(changed).isEqualTo(2);
        ArgumentCaptor<OrderInspection> created = ArgumentCaptor.forClass(OrderInspection.class);
        verify(orderInspectionRepository).save(created.capture());
        assertThat(created.getValue().getStatus()).isEqualTo(InspectionStatus.PENDING);
        assertThat(created.getValue().getDueDate())
                .isEqualTo(created.getValue().getCeremonyCompletedAt()
                        .atZone(java.time.ZoneId.systemDefault())
                        .withZoneSameInstant(SalesOrderService.PLATFORM_ZONE)
                        .toLocalDate().plusDays(7));

        assertThat(overdue.getStatus()).isEqualTo(InspectionStatus.APPROVED);
        assertThat(overdue.getApprovalType()).isEqualTo(InspectionApprovalType.DEEMED);
        assertThat(overdue.getApprovedDate()).isEqualTo(overdue.getDueDate());
        assertThat(overdue.getPayableAmount()).isEqualByComparingTo("1000000");
        ArgumentCaptor<PartnerLedgerEntry> ledger = ArgumentCaptor.forClass(PartnerLedgerEntry.class);
        verify(partnerLedgerEntryRepository).save(ledger.capture());
        assertThat(ledger.getValue().getEntryType()).isEqualTo(PartnerLedgerEntryType.PAYABLE);
        assertThat(ledger.getValue().getAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("감액해 승인하면 지급 대상 금액은 발주 금액 − 감액이고 원장에 지급 의무로 남는다")
    void approveInspection_withDeduction() {
        OrderInspection inspection = givenPendingInspection(LocalDate.now(SalesOrderService.PLATFORM_ZONE).plusDays(3));

        OrderInspectionDto.Response.InspectionSummary result = orderInspectionService.approveInspection(
                INSPECTION_ID, ADMIN_ID, ROLE,
                new OrderInspectionDto.Request.Approve(" 태블릿 1대 고장 ", null, BigDecimal.valueOf(100_000), "장비 하자")
        );

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(inspection.getApprovalType()).isEqualTo(InspectionApprovalType.MANUAL);
        assertThat(inspection.getDefectNote()).isEqualTo("태블릿 1대 고장");
        assertThat(inspection.getPayableAmount()).isEqualByComparingTo("1400000");
        assertThat(inspection.getApprovedBy()).isEqualTo(ADMIN_ID);
        ArgumentCaptor<PartnerLedgerEntry> ledger = ArgumentCaptor.forClass(PartnerLedgerEntry.class);
        verify(partnerLedgerEntryRepository).save(ledger.capture());
        assertThat(ledger.getValue().getAmount()).isEqualByComparingTo("1400000");
        assertThat(ledger.getValue().getMemo()).contains("감액 100,000", "장비 하자");
    }

    @Test
    @DisplayName("감액이 발주 금액보다 크면 승인할 수 없다")
    void approveInspection_rejectsExcessiveDeduction() {
        givenPendingInspection(LocalDate.now(SalesOrderService.PLATFORM_ZONE));

        assertErrorCode(
                () -> orderInspectionService.approveInspection(INSPECTION_ID, ADMIN_ID, ROLE,
                        new OrderInspectionDto.Request.Approve(null, null, BigDecimal.valueOf(1_500_001), "사유")),
                OrderErrorCode.INSPECTION_DEDUCTION_INVALID
        );
    }

    @Test
    @DisplayName("감액하면 사유가 필요하다")
    void approveInspection_requiresDeductionReason() {
        givenPendingInspection(LocalDate.now(SalesOrderService.PLATFORM_ZONE));

        assertErrorCode(
                () -> orderInspectionService.approveInspection(INSPECTION_ID, ADMIN_ID, ROLE,
                        new OrderInspectionDto.Request.Approve(null, null, BigDecimal.valueOf(10_000), "  ")),
                OrderErrorCode.INSPECTION_DEDUCTION_REASON_REQUIRED
        );
        verify(partnerLedgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("검수 기한이 지난 건은 직접 승인할 수 없다(승인 간주 대상)")
    void approveInspection_rejectsAfterDeadline() {
        givenPendingInspection(LocalDate.now(SalesOrderService.PLATFORM_ZONE).minusDays(1));

        assertErrorCode(
                () -> orderInspectionService.approveInspection(INSPECTION_ID, ADMIN_ID, ROLE,
                        new OrderInspectionDto.Request.Approve(null, null, null, null)),
                OrderErrorCode.INSPECTION_DEADLINE_PASSED
        );
    }

    @Test
    @DisplayName("ACTION_ORDER_INSPECTION 권한이 없으면 승인할 수 없다")
    void approveInspection_requiresPermission() {
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_ORDER_INSPECTION")).willReturn(false);

        assertErrorCode(
                () -> orderInspectionService.approveInspection(INSPECTION_ID, ADMIN_ID, "PLATFORM_SUPPORT",
                        new OrderInspectionDto.Request.Approve(null, null, null, null)),
                CommonErrorCode.ACCESS_DENIED
        );
    }

    private OrderInspection givenPendingInspection(LocalDate dueDate) {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_ORDER_INSPECTION")).willReturn(true);
        OrderInspection inspection = OrderFixtures.inspection(301L, partner, 1_500_000, dueDate);
        ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
        given(orderInspectionRepository.findById(INSPECTION_ID)).willReturn(Optional.of(inspection));
        return inspection;
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Object errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(errorCode);
    }
}
