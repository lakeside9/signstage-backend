package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingQuoteDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuote;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuoteLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuoteStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuoteStatusEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingQuoteLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingQuoteRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingQuoteStatusEventRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link BillingQuoteService}의 확정/무효화 흐름 단위 테스트 — signstage-docs
 * business/currency-tax-internationalization-review.md 9장. 계산 로직 자체는
 * {@link CeremonyService#buildQuoteCalculation}에 있으므로(별도로 검증됨,
 * {@code CeremonyServiceTest}), 이 테스트는 "그 계산 결과를 그대로 스냅샷에 옮기는지"와
 * "상태 규칙(빈 견적 거부, 중복 무효화 거부)"만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class BillingQuoteServiceTest {

    @Mock
    private BillingQuoteRepository billingQuoteRepository;
    @Mock
    private BillingQuoteLineRepository billingQuoteLineRepository;
    @Mock
    private BillingQuoteStatusEventRepository billingQuoteStatusEventRepository;
    @Mock
    private CeremonyService ceremonyService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BillingQuoteService billingQuoteService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CEREMONY_ID = 10L;
    private static final Long CURRENT_USER_ID = 1L;

    private Ceremony ceremony() {
        Organization organization = Organization.builder().name("조직").code("org").build();
        Ceremony ceremony = Ceremony.builder().organization(organization).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        return ceremony;
    }

    private CeremonyService.QuoteLineDetail line() {
        return new CeremonyService.QuoteLineDetail(
                "PLAN_UNIT_PRODUCT", 901L, "서명자", UnitProductCategory.ESSENTIAL, 1,
                new BigDecimal("10000"), new BigDecimal("10000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("10000"),
                "KR_VAT_STANDARD", "STANDARD", new BigDecimal("10.0000"), "EXCLUSIVE",
                new BigDecimal("1000"), new BigDecimal("11000")
        );
    }

    @Test
    @DisplayName("견적 확정 — 계산 결과를 헤더+줄+FINALIZED 이벤트로 저장하고, 다음 버전 번호를 매긴다")
    void finalizeQuote_savesHeaderLinesAndFinalizedEvent() {
        Ceremony ceremony = ceremony();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        CeremonyService.QuoteCalculation calculation = new CeremonyService.QuoteCalculation(
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"),
                new BigDecimal("10000"), new BigDecimal("1000"), new BigDecimal("11000"),
                List.of(line())
        );

        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID)).willReturn(member);
        given(ceremonyService.buildQuoteCalculation(ceremony)).willReturn(calculation);
        given(billingQuoteRepository.findMaxVersion(CEREMONY_ID)).willReturn(2);
        given(billingQuoteRepository.save(any(BillingQuote.class))).willAnswer(invocation -> {
            BillingQuote quote = invocation.getArgument(0);
            ReflectionTestUtils.setField(quote, "id", 501L);
            ReflectionTestUtils.setField(quote, "createdBy", CURRENT_USER_ID);
            ReflectionTestUtils.setField(quote, "createdAt", LocalDateTime.of(2026, 9, 10, 12, 0));
            return quote;
        });
        given(billingQuoteLineRepository.findAllByBillingQuoteIdOrderByIdAsc(501L)).willReturn(List.of());
        given(billingQuoteStatusEventRepository.findAllByBillingQuoteIdOrderByOccurredAtDescIdDesc(501L))
                .willReturn(List.of());

        BillingQuoteDto.Response.QuoteDetail result =
                billingQuoteService.finalizeQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID);

        assertThat(result.getSummary().getVersion()).isEqualTo(3);
        assertThat(result.getSummary().getNetAmount()).isEqualByComparingTo("10000");
        assertThat(result.getSummary().getGrossAmount()).isEqualByComparingTo("11000");

        verify(billingQuoteLineRepository, times(1)).save(any(BillingQuoteLine.class));
        verify(billingQuoteStatusEventRepository).save(argThatStatus(BillingQuoteStatus.FINALIZED));
    }

    @Test
    @DisplayName("견적 확정 — 계산 결과에 줄이 하나도 없으면(플랜 없음 등) 거부한다")
    void finalizeQuote_rejectsEmptyCalculation() {
        Ceremony ceremony = ceremony();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        CeremonyService.QuoteCalculation emptyCalculation = new CeremonyService.QuoteCalculation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );

        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID)).willReturn(member);
        given(ceremonyService.buildQuoteCalculation(ceremony)).willReturn(emptyCalculation);

        assertThatThrownBy(() -> billingQuoteService.finalizeQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.QUOTE_EMPTY);
    }

    @Test
    @DisplayName("견적 무효화 — 이미 VOID 상태인 견적은 다시 무효화할 수 없다")
    void voidQuote_rejectsAlreadyVoidQuote() {
        Ceremony ceremony = ceremony();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        BillingQuote quote = BillingQuote.builder().ceremony(ceremony).version(1)
                .currencyCode("KRW").currencyFractionDigits((short) 0).currencyRoundingMode("HALF_UP")
                .netAmount(BigDecimal.TEN).discountAmount(BigDecimal.ZERO).taxAmount(BigDecimal.ONE)
                .grossAmount(BigDecimal.TEN).pricingCalculatedAt(LocalDateTime.now())
                .taxPointDate(LocalDate.now()).build();
        ReflectionTestUtils.setField(quote, "id", 501L);

        BillingQuoteStatusEvent voidEvent = BillingQuoteStatusEvent.builder()
                .billingQuote(quote).status(BillingQuoteStatus.VOID).reason("이미 무효화됨").actorId(CURRENT_USER_ID).build();

        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID)).willReturn(member);
        given(billingQuoteRepository.findByIdAndCeremonyId(501L, CEREMONY_ID)).willReturn(Optional.of(quote));
        given(billingQuoteStatusEventRepository.findAllByBillingQuoteIdOrderByOccurredAtDescIdDesc(501L))
                .willReturn(List.of(voidEvent));

        BillingQuoteDto.Request.VoidQuote request = new BillingQuoteDto.Request.VoidQuote("재무효화 시도");

        assertThatThrownBy(() -> billingQuoteService.voidQuote(ORGANIZATION_ID, CEREMONY_ID, 501L, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.QUOTE_ALREADY_VOID);
    }

    private BillingQuoteStatusEvent argThatStatus(BillingQuoteStatus status) {
        return argThat(event -> event != null && event.getStatus() == status);
    }
}
