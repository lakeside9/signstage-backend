package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.PartnerPayoutDto;
import com.eformworks.signstage.backend.feature.order.entity.OrderInspection;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntryType;
import com.eformworks.signstage.backend.feature.order.entity.PartnerCompensation;
import com.eformworks.signstage.backend.feature.order.entity.PartnerCompensationStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffset;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffsetStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayout;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayoutStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.OrderInspectionRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerCompensationRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOffsetRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerPayoutRepository;
import com.eformworks.signstage.backend.feature.organization.dto.PartnerSubcontractProfileDto;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.service.PartnerSubcontractProfileService;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link PartnerPayoutService} 단위 테스트 — 월 합산 정산·지급 완료·취소(D11·D12). */
@ExtendWith(MockitoExtension.class)
class PartnerPayoutServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final String ROLE = "PLATFORM_OPS";

    @Mock
    private PartnerPayoutRepository partnerPayoutRepository;
    @Mock
    private OrderInspectionRepository orderInspectionRepository;
    @Mock
    private PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    @Mock
    private OrderInspectionService orderInspectionService;
    @Mock
    private PartnerOffsetRepository partnerOffsetRepository;
    @Mock
    private PartnerCompensationRepository partnerCompensationRepository;
    @Mock
    private PartnerSubcontractProfileService partnerSubcontractProfileService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;
    @Mock
    private OrderNotificationService orderNotificationService;

    private PartnerPayoutService partnerPayoutService;
    private Organization partnerA;
    private Organization partnerB;
    private YearMonth lastMonth;

    @BeforeEach
    void setUp() {
        partnerPayoutService = new PartnerPayoutService(
                partnerPayoutRepository, orderInspectionRepository, partnerLedgerEntryRepository, orderInspectionService,
                partnerOffsetRepository, partnerCompensationRepository, new PartnerSettlementPolicy(7, 60), OrderFixtures.paymentControlPolicy(),
                partnerSubcontractProfileService, memberRepository,
                rolePermissionService, auditLogRecorder, orderNotificationService
        );
        partnerA = OrderFixtures.organization(7L);
        partnerB = OrderFixtures.organization(8L);
        lastMonth = YearMonth.now(SalesOrderService.PLATFORM_ZONE).minusMonths(1);
    }

    @Test
    @DisplayName("이번 달은 아직 마감 전이라 정산할 수 없다")
    void createPayouts_rejectsOpenMonth() {
        allowPayment();

        assertErrorCode(
                () -> partnerPayoutService.createPayouts(YearMonth.now(SalesOrderService.PLATFORM_ZONE).toString(), ADMIN_ID, ROLE),
                OrderErrorCode.PAYOUT_MONTH_NOT_CLOSED
        );
    }

    @Test
    @DisplayName("승인월의 미정산 검수를 파트너별로 합산하고 계좌를 스냅샷한다")
    void createPayouts_groupsByPartner() {
        allowPayment();
        OrderInspection a1 = approved(301L, partnerA, 1_000_000, lastMonth.atDay(3));
        OrderInspection a2 = approved(302L, partnerA, 500_000, lastMonth.atDay(20));
        OrderInspection b1 = approved(303L, partnerB, 800_000, lastMonth.atDay(10));
        given(orderInspectionRepository.findSettleableUntil(lastMonth.atEndOfMonth(), disputeWindowFrom()))
                .willReturn(List.of(a1, a2, b1));
        PartnerSubcontractProfileDto.Response.Profile profileA = mock(PartnerSubcontractProfileDto.Response.Profile.class);
        given(profileA.getBankName()).willReturn("국민은행");
        given(partnerSubcontractProfileService.retrieveProfileByPlatformAdmin(7L)).willReturn(profileA);
        given(partnerSubcontractProfileService.retrieveProfileByPlatformAdmin(8L))
                .willReturn(PartnerSubcontractProfileDto.Response.Profile.empty(8L));
        given(partnerPayoutRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        List<PartnerPayoutDto.Response.PayoutSummary> created =
                partnerPayoutService.createPayouts(lastMonth.toString(), ADMIN_ID, ROLE);

        verify(orderInspectionService).syncInspections();
        assertThat(created).hasSize(2);
        PartnerPayoutDto.Response.PayoutSummary payoutA = created.getFirst();
        assertThat(payoutA.getOrganizationId()).isEqualTo(7L);
        assertThat(payoutA.getAmount()).isEqualByComparingTo("1500000");
        assertThat(payoutA.getStatus()).isEqualTo("CONFIRMED");
        assertThat(payoutA.getBankName()).isEqualTo("국민은행");
        assertThat(payoutA.getDueDate()).isEqualTo(new PartnerSettlementPolicy(7, 60).paymentDueDate(lastMonth, lastMonth.atDay(3)));
        assertThat(created.get(1).getAmount()).isEqualByComparingTo("800000");
        assertThat(a1.getPartnerPayout()).isNotNull().isSameAs(a2.getPartnerPayout());
        assertThat(b1.getPartnerPayout()).isNotSameAs(a1.getPartnerPayout());
    }

    @Test
    @DisplayName("고객 취소 보상은 검수 건이 없어도 그달 지급 건으로 만들고, 검수 건이 있으면 합산한다(C04)")
    void createPayouts_includesCompensations() {
        allowPayment();
        OrderInspection a1 = approved(301L, partnerA, 1_000_000, lastMonth.atDay(3));
        given(orderInspectionRepository.findSettleableUntil(lastMonth.atEndOfMonth(), disputeWindowFrom()))
                .willReturn(List.of(a1));
        PartnerCompensation compensationA = compensation(partnerA, 200_000, lastMonth.atDay(15));
        PartnerCompensation compensationB = compensation(partnerB, 300_000, lastMonth.atDay(1));
        given(partnerCompensationRepository.findPendingUntil(lastMonth.atEndOfMonth()))
                .willReturn(List.of(compensationA, compensationB));
        given(partnerSubcontractProfileService.retrieveProfileByPlatformAdmin(any()))
                .willAnswer(invocation -> PartnerSubcontractProfileDto.Response.Profile.empty(invocation.getArgument(0)));
        given(partnerPayoutRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        List<PartnerPayoutDto.Response.PayoutSummary> created =
                partnerPayoutService.createPayouts(lastMonth.toString(), ADMIN_ID, ROLE);

        assertThat(created).extracting(PartnerPayoutDto.Response.PayoutSummary::getOrganizationId,
                        PartnerPayoutDto.Response.PayoutSummary::getAmount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(7L, new BigDecimal("1200000")),
                        org.assertj.core.groups.Tuple.tuple(8L, new BigDecimal("300000")));
        assertThat(compensationA.getStatus()).isEqualTo(PartnerCompensationStatus.APPLIED);
        assertThat(compensationA.getPartnerPayout()).isSameAs(a1.getPartnerPayout());
        assertThat(compensationB.getPartnerPayout()).isNotNull();
    }

    @Test
    @DisplayName("지급 완료를 기록하면 원장에 지급액(−)이 남는다")
    void markPaid_recordsLedgerPayment() {
        allowPayment();
        PartnerPayout payout = givenPayout(PartnerPayoutStatus.CONFIRMED);
        LocalDate today = LocalDate.now(SalesOrderService.PLATFORM_ZONE);

        partnerPayoutService.markPaid(900L, ADMIN_ID, ROLE,
                new PartnerPayoutDto.Request.MarkPaid(today, BigDecimal.valueOf(1_650_000), "부가세 포함 이체"));

        assertThat(payout.getStatus()).isEqualTo(PartnerPayoutStatus.PAID);
        assertThat(payout.getPaidBy()).isEqualTo(ADMIN_ID);
        assertThat(payout.getTransferredAmount()).isEqualByComparingTo("1650000");
        ArgumentCaptor<PartnerLedgerEntry> ledger = ArgumentCaptor.forClass(PartnerLedgerEntry.class);
        verify(partnerLedgerEntryRepository).save(ledger.capture());
        assertThat(ledger.getValue().getEntryType()).isEqualTo(PartnerLedgerEntryType.PAYMENT);
        assertThat(ledger.getValue().getAmount()).isEqualByComparingTo("-1500000");
        assertThat(ledger.getValue().getOccurredOn()).isEqualTo(today);
    }

    @Test
    @DisplayName("이미 지급한 건은 다시 지급 완료·취소할 수 없다")
    void markPaid_rejectsPaidPayout() {
        allowPayment();
        givenPayout(PartnerPayoutStatus.PAID);

        assertErrorCode(
                () -> partnerPayoutService.cancelPayout(900L, ADMIN_ID, ROLE, "중복"),
                OrderErrorCode.PAYOUT_NOT_CONFIRMED
        );
    }

    @Test
    @DisplayName("지급일은 미래일 수 없다")
    void markPaid_rejectsFuturePaidDate() {
        allowPayment();
        givenPayout(PartnerPayoutStatus.CONFIRMED);

        assertErrorCode(
                () -> partnerPayoutService.markPaid(900L, ADMIN_ID, ROLE, new PartnerPayoutDto.Request.MarkPaid(
                        LocalDate.now(SalesOrderService.PLATFORM_ZONE).plusDays(1), BigDecimal.ONE, null)),
                OrderErrorCode.PAYOUT_PAID_DATE_INVALID
        );
    }

    @Test
    @DisplayName("지급을 취소하면 묶였던 검수가 풀린다")
    void cancelPayout_releasesInspections() {
        allowPayment();
        PartnerPayout payout = givenPayout(PartnerPayoutStatus.CONFIRMED);
        OrderInspection item = approved(301L, partnerA, 1_500_000, lastMonth.atDay(3));
        item.assignPayout(payout);
        given(orderInspectionRepository.findAllByPartnerPayoutId(900L)).willReturn(List.of(item));

        partnerPayoutService.cancelPayout(900L, ADMIN_ID, ROLE, " 계좌 오류 ");

        assertThat(payout.getStatus()).isEqualTo(PartnerPayoutStatus.CANCELLED);
        assertThat(payout.getCancellationReason()).isEqualTo("계좌 오류");
        assertThat(item.getPartnerPayout()).isNull();
    }

    @Test
    @DisplayName("귀책 상계를 지급액에서 빼고, 검수 합계를 넘는 상계는 이월한다")
    void createPayouts_appliesOffsetsAndCarriesOver() {
        allowPayment();
        OrderInspection a1 = approved(301L, partnerA, 1_000_000, lastMonth.atDay(3));
        given(orderInspectionRepository.findSettleableUntil(lastMonth.atEndOfMonth(), disputeWindowFrom()))
                .willReturn(List.of(a1));
        given(partnerSubcontractProfileService.retrieveProfileByPlatformAdmin(7L))
                .willReturn(PartnerSubcontractProfileDto.Response.Profile.empty(7L));
        given(partnerPayoutRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        PartnerOffset first = offset(600_000, lastMonth.atDay(5));
        PartnerOffset second = offset(700_000, lastMonth.atDay(9));
        given(partnerOffsetRepository.findPendingForSettlement(7L, "KRW", lastMonth.atEndOfMonth()))
                .willReturn(List.of(first, second));

        PartnerPayoutDto.Response.PayoutSummary created =
                partnerPayoutService.createPayouts(lastMonth.toString(), ADMIN_ID, ROLE).getFirst();

        assertThat(created.getGrossAmount()).isEqualByComparingTo("1000000");
        assertThat(created.getOffsetAmount()).isEqualByComparingTo("1000000");
        assertThat(created.getAmount()).isEqualByComparingTo("0");
        assertThat(first.getStatus()).isEqualTo(PartnerOffsetStatus.APPLIED);
        assertThat(second.getStatus()).isEqualTo(PartnerOffsetStatus.APPLIED);
        ArgumentCaptor<PartnerOffset> carry = ArgumentCaptor.forClass(PartnerOffset.class);
        verify(partnerOffsetRepository).save(carry.capture());
        assertThat(carry.getValue().getAmount()).isEqualByComparingTo("300000");
        assertThat(carry.getValue().isCarryOver()).isTrue();
        assertThat(carry.getValue().isPending()).isTrue();
    }

    @Test
    @DisplayName("고액 지급은 정산을 만들지 않은 SUPER가 승인해야 지급 완료로 기록할 수 있다")
    void largePayout_requiresApprovalByAnotherSuper() {
        allowPayment();
        PartnerPayout payout = givenLargePayout(ADMIN_ID);
        PartnerPayoutDto.Request.MarkPaid markPaid = new PartnerPayoutDto.Request.MarkPaid(
                LocalDate.now(SalesOrderService.PLATFORM_ZONE), BigDecimal.valueOf(13_200_000), null);

        assertErrorCode(() -> partnerPayoutService.markPaid(900L, ADMIN_ID, ROLE, markPaid), OrderErrorCode.PAYOUT_APPROVAL_REQUIRED);

        given(rolePermissionService.isAllowed("PLATFORM_SUPER", "ACTION_PARTNER_PAYOUT_LARGE_APPROVE")).willReturn(true);
        assertErrorCode(() -> partnerPayoutService.approvePayout(900L, ADMIN_ID, "PLATFORM_SUPER"),
                OrderErrorCode.PAYOUT_APPROVER_IS_CREATOR);

        partnerPayoutService.approvePayout(900L, 2L, "PLATFORM_SUPER");
        assertThat(payout.getApprovedBy()).isEqualTo(2L);
        assertThat(payout.isAwaitingApproval()).isFalse();
        assertErrorCode(() -> partnerPayoutService.approvePayout(900L, 2L, "PLATFORM_SUPER"),
                OrderErrorCode.PAYOUT_APPROVAL_NOT_NEEDED);

        partnerPayoutService.markPaid(900L, ADMIN_ID, ROLE, markPaid);
        assertThat(payout.getStatus()).isEqualTo(PartnerPayoutStatus.PAID);
    }

    @Test
    @DisplayName("OPS는 고액 지급을 승인할 수 없다")
    void approvePayout_requiresPermission() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_PARTNER_PAYOUT_LARGE_APPROVE")).willReturn(false);

        assertErrorCode(() -> partnerPayoutService.approvePayout(900L, 2L, ROLE), CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("지급을 취소하면 상계는 대기로 돌아가고 이월 상계는 취소된다. 이월이 이미 반영됐으면 취소할 수 없다")
    void cancelPayout_releasesOffsets() {
        allowPayment();
        PartnerPayout payout = givenPayout(PartnerPayoutStatus.CONFIRMED);
        PartnerOffset applied = offset(600_000, lastMonth.atDay(5));
        applied.applyTo(payout);
        PartnerOffset carry = applied.carryOver(BigDecimal.valueOf(100_000), payout, lastMonth.atEndOfMonth());
        given(partnerOffsetRepository.findAllByPartnerPayoutId(900L)).willReturn(List.of(applied));
        given(partnerOffsetRepository.findAllByCarriedOverFromPayoutId(900L)).willReturn(List.of(carry));

        carry.applyTo(mock(PartnerPayout.class));
        assertErrorCode(() -> partnerPayoutService.cancelPayout(900L, ADMIN_ID, ROLE, "재정산"),
                OrderErrorCode.PAYOUT_CARRY_OVER_APPLIED);

        carry.release();
        partnerPayoutService.cancelPayout(900L, ADMIN_ID, ROLE, "재정산");
        assertThat(applied.isPending()).isTrue();
        assertThat(applied.getPartnerPayout()).isNull();
        assertThat(carry.getStatus()).isEqualTo(PartnerOffsetStatus.CANCELLED);
    }

    @Test
    @DisplayName("파트너 OPERATOR는 지급 내역을 볼 수 없다")
    void findReceivedPayouts_rejectsOperator() {
        Member member = Member.builder().organization(partnerA).role(MemberRole.OPERATOR).status(MemberStatus.ACTIVE).build();
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(7L, 70L, MemberStatus.ACTIVE)).willReturn(Optional.of(member));
        given(rolePermissionService.isAllowed("OPERATOR", "ACTION_PARTNER_PAYOUT_VIEW")).willReturn(false);

        assertErrorCode(
                () -> partnerPayoutService.findReceivedPayouts(7L, 70L, Pageable.unpaged()),
                CommonErrorCode.ACCESS_DENIED
        );
    }

    private void allowPayment() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_PARTNER_PAYMENT_APPROVE")).willReturn(true);
    }

    private PartnerPayout givenPayout(PartnerPayoutStatus status) {
        PartnerPayout payout = PartnerPayout.builder()
                .organization(partnerA)
                .settlementMonth(lastMonth.toString())
                .currencyCode("KRW")
                .grossAmount(BigDecimal.valueOf(1_500_000))
                .dueDate(lastMonth.plusMonths(1).atEndOfMonth())
                .build();
        ReflectionTestUtils.setField(payout, "id", 900L);
        if (status == PartnerPayoutStatus.PAID) {
            payout.markPaid(ADMIN_ID, LocalDate.now(), BigDecimal.ONE, null);
        }
        given(partnerPayoutRepository.findById(900L)).willReturn(Optional.of(payout));
        return payout;
    }

    private PartnerPayout givenLargePayout(Long createdBy) {
        PartnerPayout payout = PartnerPayout.builder()
                .organization(partnerA)
                .settlementMonth(lastMonth.toString())
                .currencyCode("KRW")
                .grossAmount(BigDecimal.valueOf(12_000_000))
                .dueDate(lastMonth.plusMonths(1).atEndOfMonth())
                .approvalRequired(true)
                .build();
        ReflectionTestUtils.setField(payout, "id", 900L);
        ReflectionTestUtils.setField(payout, "createdBy", createdBy);
        given(partnerPayoutRepository.findById(900L)).willReturn(Optional.of(payout));
        return payout;
    }

    private static PartnerCompensation compensation(Organization partner, long amount, LocalDate occurredOn) {
        return PartnerCompensation.builder()
                .partnerOrder(OrderFixtures.partnerOrder(320L + amount / 100_000, partner, amount * 5))
                .amount(BigDecimal.valueOf(amount))
                .ratePercent(BigDecimal.valueOf(20))
                .reason("고객 취소")
                .occurredOn(occurredOn)
                .build();
    }

    private PartnerOffset offset(long amount, LocalDate occurredOn) {
        return PartnerOffset.builder()
                .partnerOrder(OrderFixtures.partnerOrder(310L, partnerA, 1_000_000))
                .amount(BigDecimal.valueOf(amount))
                .reason("고객 환불 귀책")
                .occurredOn(occurredOn)
                .build();
    }

    private static LocalDate disputeWindowFrom() {
        return LocalDate.now(SalesOrderService.PLATFORM_ZONE).minusDays(7);
    }

    private static OrderInspection approved(Long partnerOrderId, Organization partner, long amount, LocalDate approvedDate) {
        OrderInspection inspection = OrderFixtures.inspection(partnerOrderId, partner, amount, approvedDate.plusDays(2));
        inspection.approve(ADMIN_ID, approvedDate, null, null, BigDecimal.ZERO, null, null, null);
        return inspection;
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Object errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(errorCode);
    }
}
