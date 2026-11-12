package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.PartnerOffsetDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntryType;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffset;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffsetStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayout;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.PartnerLedgerEntryRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOffsetRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link PartnerOffsetService} 단위 테스트 — 귀책 상계 등록·취소(D13). */
@ExtendWith(MockitoExtension.class)
class PartnerOffsetServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final String ROLE = "PLATFORM_OPS";

    @Mock
    private PartnerOffsetRepository partnerOffsetRepository;
    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private PartnerLedgerEntryRepository partnerLedgerEntryRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    @InjectMocks
    private PartnerOffsetService partnerOffsetService;

    private PartnerOrder partnerOrder;

    @BeforeEach
    void setUp() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_PARTNER_PAYMENT_APPROVE")).willReturn(true);
        partnerOrder = OrderFixtures.partnerOrder(301L, OrderFixtures.organization(7L), 1_500_000);
    }

    @Test
    @DisplayName("수락된 발주에 상계를 등록하면 원장에 상계(−)가 남는다")
    void registerOffset_recordsLedger() {
        partnerOrder.accept(70L, null);
        given(partnerOrderRepository.findById(301L)).willReturn(Optional.of(partnerOrder));
        given(partnerOffsetRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PartnerOffsetDto.Response.OffsetSummary result = partnerOffsetService.registerOffset(ADMIN_ID, ROLE,
                new PartnerOffsetDto.Request.Register(301L, BigDecimal.valueOf(200_000), " 고객 환불 귀책 ", null));

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getReason()).isEqualTo("고객 환불 귀책");
        assertThat(result.getOccurredOn()).isEqualTo(LocalDate.now(SalesOrderService.PLATFORM_ZONE));
        ArgumentCaptor<PartnerLedgerEntry> ledger = ArgumentCaptor.forClass(PartnerLedgerEntry.class);
        verify(partnerLedgerEntryRepository).save(ledger.capture());
        assertThat(ledger.getValue().getEntryType()).isEqualTo(PartnerLedgerEntryType.OFFSET);
        assertThat(ledger.getValue().getAmount()).isEqualByComparingTo("-200000");
    }

    @Test
    @DisplayName("수락되지 않은 발주나 발주 금액을 넘는 상계는 등록할 수 없다")
    void registerOffset_validates() {
        given(partnerOrderRepository.findById(301L)).willReturn(Optional.of(partnerOrder));

        assertErrorCode(() -> partnerOffsetService.registerOffset(ADMIN_ID, ROLE,
                new PartnerOffsetDto.Request.Register(301L, BigDecimal.ONE, "환불", null)),
                OrderErrorCode.OFFSET_PARTNER_ORDER_INVALID);

        partnerOrder.accept(70L, null);
        assertErrorCode(() -> partnerOffsetService.registerOffset(ADMIN_ID, ROLE,
                new PartnerOffsetDto.Request.Register(301L, BigDecimal.valueOf(1_500_001), "환불", null)),
                OrderErrorCode.OFFSET_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("대기 상계를 취소하면 원장에 취소(+)가 남고, 반영됐거나 이월된 상계는 취소할 수 없다")
    void cancelOffset() {
        PartnerOffset offset = PartnerOffset.builder()
                .partnerOrder(partnerOrder).amount(BigDecimal.valueOf(200_000)).reason("환불").occurredOn(LocalDate.now()).build();
        given(partnerOffsetRepository.findById(10L)).willReturn(Optional.of(offset));

        partnerOffsetService.cancelOffset(10L, ADMIN_ID, ROLE, "중복 등록");

        assertThat(offset.getStatus()).isEqualTo(PartnerOffsetStatus.CANCELLED);
        ArgumentCaptor<PartnerLedgerEntry> ledger = ArgumentCaptor.forClass(PartnerLedgerEntry.class);
        verify(partnerLedgerEntryRepository).save(ledger.capture());
        assertThat(ledger.getValue().getAmount()).isEqualByComparingTo("200000");

        PartnerOffset carry = offset.carryOver(BigDecimal.TEN, Mockito.mock(PartnerPayout.class), LocalDate.now());
        given(partnerOffsetRepository.findById(11L)).willReturn(Optional.of(carry));
        assertErrorCode(() -> partnerOffsetService.cancelOffset(11L, ADMIN_ID, ROLE, "x"), OrderErrorCode.OFFSET_NOT_CANCELLABLE);
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Object errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(errorCode);
    }
}
