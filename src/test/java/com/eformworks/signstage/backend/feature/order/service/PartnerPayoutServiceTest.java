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
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayout;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayoutStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.OrderInspectionRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
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
    private PartnerSubcontractProfileService partnerSubcontractProfileService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    private PartnerPayoutService partnerPayoutService;
    private Organization partnerA;
    private Organization partnerB;
    private YearMonth lastMonth;

    @BeforeEach
    void setUp() {
        partnerPayoutService = new PartnerPayoutService(
                partnerPayoutRepository, orderInspectionRepository, partnerLedgerEntryRepository, orderInspectionService,
                new PartnerSettlementPolicy(7, 60), partnerSubcontractProfileService, memberRepository,
                rolePermissionService, auditLogRecorder
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
        given(orderInspectionRepository.findUnsettledApprovedBetween(lastMonth.atDay(1), lastMonth.atEndOfMonth()))
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
                .amount(BigDecimal.valueOf(1_500_000))
                .dueDate(lastMonth.plusMonths(1).atEndOfMonth())
                .build();
        ReflectionTestUtils.setField(payout, "id", 900L);
        if (status == PartnerPayoutStatus.PAID) {
            payout.markPaid(ADMIN_ID, LocalDate.now(), BigDecimal.ONE, null);
        }
        given(partnerPayoutRepository.findById(900L)).willReturn(Optional.of(payout));
        return payout;
    }

    private static OrderInspection approved(Long partnerOrderId, Organization partner, long amount, LocalDate approvedDate) {
        OrderInspection inspection = OrderFixtures.inspection(partnerOrderId, partner, amount, approvedDate.plusDays(2));
        inspection.approve(ADMIN_ID, approvedDate, null, null, BigDecimal.ZERO, null);
        return inspection;
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Object errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(errorCode);
    }
}
