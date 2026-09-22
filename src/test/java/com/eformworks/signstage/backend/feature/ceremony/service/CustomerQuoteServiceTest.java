package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.ceremony.dto.CustomerQuoteDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CustomerQuote;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.MarginInfo;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationMarginPolicy;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyMarginOverrideRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CustomerQuoteLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CustomerQuoteRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationMarginPolicyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyMarginOverride;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CustomerQuoteService#generateCustomerQuote}의 장비/인력 품목 검증 단위 테스트 —
 * 2026-09-11 사용자 지적("미사용 단위상품도 품목에 추가되고, 배타 상품도 제약없이 추가됩니다")에
 * 따른 방어 로직 검증. {@link CeremonyService}는 협력 객체로 통째로 목(mock) 처리한다 —
 * {@code checkExclusivityGroups}/{@code resolveSellableUnitProductPeriod} 자체의 실제 판정
 * 로직은 {@link CeremonyServiceTest}가 검증하고, 여기서는 "이 두 검사를 실제로 호출하고 그
 * 예외를 그대로 전파하는지"(배선)만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CustomerQuoteServiceTest {

    @Mock
    private OrganizationMarginPolicyRepository organizationMarginPolicyRepository;
    @Mock
    private CeremonyMarginOverrideRepository ceremonyMarginOverrideRepository;
    @Mock
    private CustomerQuoteRepository customerQuoteRepository;
    @Mock
    private CustomerQuoteLineRepository customerQuoteLineRepository;
    @Mock
    private UnitProductRepository unitProductRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CeremonyService ceremonyService;
    @Spy
    private MoneyCalculator moneyCalculator = new MoneyCalculator();
    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private CustomerQuoteService customerQuoteService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CEREMONY_ID = 10L;
    private static final Long CURRENT_USER_ID = 1L;

    @BeforeEach
    void setUpPermissions() {
        lenient().when(rolePermissionService.isAllowed(eq("OWNER"), anyString())).thenReturn(true);
    }

    private Ceremony ceremony() {
        Ceremony ceremony = Ceremony.builder().title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        ceremony.confirmPlan();
        return ceremony;
    }

    private UnitProduct unitProduct(Long id, String name, UnitProductCategory category, String exclusivityGroup) {
        UnitProduct unitProduct = UnitProduct.builder()
                .type(UnitProductType.ONSITE_SUPPORT).name(name).category(category).exclusivityGroup(exclusivityGroup).build();
        ReflectionTestUtils.setField(unitProduct, "id", id);
        return unitProduct;
    }

    private void stubCommon(Ceremony ceremony) {
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID))
                .willReturn(Member.builder().role(MemberRole.OWNER).build());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(
                com.eformworks.signstage.backend.feature.organization.entity.Organization.builder().build()));
        given(organizationMarginPolicyRepository.findEffective(eq(ORGANIZATION_ID), any(LocalDate.class)))
                .willReturn(Optional.of(OrganizationMarginPolicy.builder()
                        .margin(new MarginInfo(DiscountType.PERCENT, BigDecimal.TEN)).build()));
        given(ceremonyService.buildQuoteCalculation(ceremony))
                .willReturn(new CeremonyService.QuoteCalculation(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
                ));
    }

    private Organization stubPeriodAccess() {
        Organization organization = Organization.builder().defaultTimeZoneId("Asia/Seoul").build();
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(Member.builder().role(MemberRole.OWNER).build()));
        given(organizationRepository.findByIdForMarginUpdate(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        return organization;
    }

    private CustomerQuoteDto.Request.MarginPeriod periodRequest(LocalDate from, LocalDate to) {
        return new CustomerQuoteDto.Request.MarginPeriod("PERCENT", BigDecimal.ZERO, from, to);
    }

    @Test
    void marginPeriod_acceptsZeroAndSameDay() {
        stubPeriodAccess();
        LocalDate date = LocalDate.of(2026, 9, 22);
        var result = customerQuoteService.saveOrganizationMarginPeriod(ORGANIZATION_ID, null, CURRENT_USER_ID, periodRequest(date, date));
        assertThat(result.getMarginValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getEffectiveFrom()).isEqualTo(date);
        assertThat(result.getEffectiveTo()).isEqualTo(date);
        verify(organizationRepository).findByIdForMarginUpdate(ORGANIZATION_ID);
        verify(organizationMarginPolicyRepository).findAllForUpdate(ORGANIZATION_ID);
    }

    @Test
    void marginPeriod_rejectsInvertedDates() {
        stubPeriodAccess();
        LocalDate date = LocalDate.of(2026, 9, 22);
        assertThatThrownBy(() -> customerQuoteService.saveOrganizationMarginPeriod(
                ORGANIZATION_ID, null, CURRENT_USER_ID, periodRequest(date, date.minusDays(1))))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(CeremonyErrorCode.MARGIN_PERIOD_INVALID);
        verify(organizationMarginPolicyRepository, never()).save(any());
    }

    @Test
    void marginPeriod_omittedEndDefaultsToDatabaseMaximum() {
        stubPeriodAccess();
        var result = customerQuoteService.saveOrganizationMarginPeriod(ORGANIZATION_ID, null, CURRENT_USER_ID,
                periodRequest(LocalDate.of(2026, 9, 22), null));
        assertThat(result.getEffectiveTo()).isEqualTo(LocalDate.of(9999, 12, 31));
        ArgumentCaptor<OrganizationMarginPolicy> saved = ArgumentCaptor.forClass(OrganizationMarginPolicy.class);
        verify(organizationMarginPolicyRepository).save(saved.capture());
        assertThat(saved.getValue().getEffectiveTo()).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    void marginPeriod_rejectsDatesOutsideDatabaseRange() {
        stubPeriodAccess();
        for (var request : List.of(
                periodRequest(LocalDate.of(2026, 1, 1), LocalDate.of(10000, 1, 1)),
                periodRequest(LocalDate.of(999, 12, 31), null))) {
            assertThatThrownBy(() -> customerQuoteService.saveOrganizationMarginPeriod(
                    ORGANIZATION_ID, null, CURRENT_USER_ID, request))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(CeremonyErrorCode.MARGIN_PERIOD_INVALID);
        }
        verify(organizationMarginPolicyRepository, never()).save(any());
    }

    @Test
    void marginPeriod_rejectsInclusiveBoundaryOverlapButAllowsNextDay() {
        Organization organization = stubPeriodAccess();
        LocalDate date = LocalDate.of(2026, 9, 22);
        given(organizationMarginPolicyRepository.findAllForUpdate(ORGANIZATION_ID)).willReturn(List.of(
                OrganizationMarginPolicy.builder().organization(organization).effectiveFrom(date.minusDays(10)).effectiveTo(date).build()));
        assertThatThrownBy(() -> customerQuoteService.saveOrganizationMarginPeriod(
                ORGANIZATION_ID, null, CURRENT_USER_ID, periodRequest(date, date.plusDays(10))))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(CeremonyErrorCode.MARGIN_PERIOD_OVERLAP);
        customerQuoteService.saveOrganizationMarginPeriod(ORGANIZATION_ID, null, CURRENT_USER_ID,
                periodRequest(date.plusDays(1), date.plusDays(10)));
        verify(organizationMarginPolicyRepository).save(any());
    }

    @Test
    void marginPeriod_legacyOpenEndMustBeClosedBeforeAdding() {
        Organization organization = stubPeriodAccess();
        LocalDate date = LocalDate.of(2026, 9, 22);
        OrganizationMarginPolicy legacy = OrganizationMarginPolicy.builder().organization(organization)
                .effectiveFrom(date.minusDays(10)).margin(new MarginInfo(DiscountType.PERCENT, BigDecimal.TEN)).build();
        ReflectionTestUtils.setField(legacy, "id", 7L);
        given(organizationMarginPolicyRepository.findAllForUpdate(ORGANIZATION_ID)).willReturn(List.of(legacy));
        assertThatThrownBy(() -> customerQuoteService.saveOrganizationMarginPeriod(
                ORGANIZATION_ID, null, CURRENT_USER_ID, periodRequest(date, date.plusDays(10))))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(CeremonyErrorCode.MARGIN_PERIOD_OVERLAP);
        given(organizationMarginPolicyRepository.findByIdAndOrganizationId(7L, ORGANIZATION_ID)).willReturn(Optional.of(legacy));
        customerQuoteService.saveOrganizationMarginPeriod(ORGANIZATION_ID, 7L, CURRENT_USER_ID,
                periodRequest(date.minusDays(10), date.minusDays(1)));
        customerQuoteService.saveOrganizationMarginPeriod(ORGANIZATION_ID, null, CURRENT_USER_ID,
                periodRequest(date, date.plusDays(10)));
        assertThat(legacy.getEffectiveTo()).isEqualTo(date.minusDays(1));
    }

    @Test
    void marginPeriod_cannotEditAnotherOrganizationsPolicy() {
        stubPeriodAccess();
        LocalDate date = LocalDate.of(2026, 9, 22);
        assertThatThrownBy(() -> customerQuoteService.saveOrganizationMarginPeriod(
                ORGANIZATION_ID, 99L, CURRENT_USER_ID, periodRequest(date, date)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(CeremonyErrorCode.MARGIN_POLICY_NOT_FOUND);
        verify(organizationMarginPolicyRepository, never()).save(any());
    }

    @Test
    void marginPeriod_rejectsEarlierAndGapPeriodsEvenWithoutOverlap() {
        stubPeriodAccess();
        given(organizationMarginPolicyRepository.findAllForUpdate(ORGANIZATION_ID)).willReturn(List.of(
                policy(2L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                policy(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))));
        for (int month : List.of(8, 10)) {
            assertThatThrownBy(() -> customerQuoteService.saveOrganizationMarginPeriod(ORGANIZATION_ID, null, CURRENT_USER_ID,
                    periodRequest(LocalDate.of(2026, month, 1), LocalDate.of(2026, month, 31))))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                    .isEqualTo(CeremonyErrorCode.MARGIN_PERIOD_START_NOT_AFTER_LAST_END);
        }
        verify(organizationMarginPolicyRepository, never()).save(any());
        customerQuoteService.saveOrganizationMarginPeriod(ORGANIZATION_ID, null, CURRENT_USER_ID,
                periodRequest(LocalDate.of(2026, 12, 1), null));
        verify(organizationMarginPolicyRepository).save(any());
    }

    @Test
    void marginPeriod_editEarlierPolicyStillAllowed() {
        stubPeriodAccess();
        OrganizationMarginPolicy earlier = policy(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        given(organizationMarginPolicyRepository.findByIdAndOrganizationId(1L, ORGANIZATION_ID)).willReturn(Optional.of(earlier));
        given(organizationMarginPolicyRepository.findAllForUpdate(ORGANIZATION_ID)).willReturn(List.of(earlier,
                policy(2L, LocalDate.of(2026, 10, 1), LocalDate.of(9999, 12, 31))));
        customerQuoteService.saveOrganizationMarginPeriod(ORGANIZATION_ID, 1L, CURRENT_USER_ID,
                periodRequest(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29)));
        assertThat(earlier.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void marginPeriod_requiresActiveMembership() {
        assertThatThrownBy(() -> customerQuoteService.retrieveOrganizationMarginPeriods(2L, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(com.eformworks.signstage.backend.core.error.CommonErrorCode.ACCESS_DENIED);
        verify(organizationMarginPolicyRepository, never()).findAllByOrganizationIdOrderByEffectiveFromDesc(any());
    }

    @Test
    void marginPeriod_statusIncludesBothBoundaries() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(Member.builder().role(MemberRole.OWNER).build()));
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(
                Organization.builder().defaultTimeZoneId("Asia/Seoul").build()));
        given(organizationMarginPolicyRepository.findAllByOrganizationIdOrderByEffectiveFromDesc(ORGANIZATION_ID)).willReturn(
                List.of(policy(1L, today.plusDays(1), today.plusDays(2)), policy(2L, today, today),
                        policy(3L, today.minusDays(2), today.minusDays(1))));
        assertThat(customerQuoteService.retrieveOrganizationMarginPeriods(ORGANIZATION_ID, CURRENT_USER_ID))
                .extracting(CustomerQuoteDto.Response.MarginPeriod::getStatus).containsExactly("SCHEDULED", "ACTIVE", "EXPIRED");
    }

    private OrganizationMarginPolicy policy(Long id, LocalDate from, LocalDate to) {
        OrganizationMarginPolicy policy = OrganizationMarginPolicy.builder().effectiveFrom(from).effectiveTo(to)
                .margin(new MarginInfo(DiscountType.PERCENT, BigDecimal.ZERO)).build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    @Test
    void generateQuote_snapshotsZeroPercentPolicyAndSurvivesPolicyEdits() {
        Ceremony ceremony = ceremony();
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID))
                .willReturn(Member.builder().role(MemberRole.OWNER).build());
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Pacific/Kiritimati"));
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(
                Organization.builder().defaultTimeZoneId("Pacific/Kiritimati").build()));
        OrganizationMarginPolicy policy = policy(12L, today, today.plusDays(30));
        given(organizationMarginPolicyRepository.findEffective(eq(ORGANIZATION_ID), any(LocalDate.class)))
                .willReturn(Optional.of(policy));
        CeremonyService.QuoteLineDetail line = org.mockito.Mockito.mock(CeremonyService.QuoteLineDetail.class);
        given(line.platformUsageFee()).willReturn(true);
        given(line.netAmount()).willReturn(BigDecimal.valueOf(10000));
        given(ceremonyService.buildQuoteCalculation(ceremony)).willReturn(new CeremonyService.QuoteCalculation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(line)));
        java.util.concurrent.atomic.AtomicReference<CustomerQuote> saved = new java.util.concurrent.atomic.AtomicReference<>();
        given(customerQuoteRepository.save(any())).willAnswer(invocation -> {
            CustomerQuote quote = invocation.getArgument(0);
            ReflectionTestUtils.setField(quote, "id", 20L);
            saved.set(quote);
            return quote;
        });
        given(customerQuoteRepository.findByIdAndCeremonyId(20L, CEREMONY_ID)).willAnswer(invocation -> Optional.of(saved.get()));
        var result = customerQuoteService.generateCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID,
                new CustomerQuoteDto.Request.GenerateQuote(List.of()));
        assertThat(result.getSummary().getSystemUsageCustomerAmount()).isEqualByComparingTo("10000");
        assertThat(result.getSummary().getSystemUsageMarginAmount()).isEqualByComparingTo("0");
        assertThat(result.getSummary().getMarginPolicySnapshot().getSourceId()).isEqualTo(12L);
        assertThat(result.getSummary().getMarginPolicySnapshot().getAppliedOn()).isEqualTo(today);
        assertThat(result.getSummary().getMarginPolicySnapshot().getTimeZoneId()).isEqualTo("Pacific/Kiritimati");
        verify(organizationMarginPolicyRepository).findEffective(ORGANIZATION_ID, today);
        policy.updatePeriod(new MarginInfo(DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(50000)), today, today);
        var historical = customerQuoteService.findCustomerQuoteDetail(ORGANIZATION_ID, CEREMONY_ID, 20L, CURRENT_USER_ID).getSummary();
        assertThat(historical.getMarginValue()).isEqualByComparingTo("0");
        assertThat(historical.getMarginPolicySnapshot().getEffectiveTo()).isEqualTo(today.plusDays(30));
        assertThat(historical.getTotalCustomerAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void effectiveMargin_overrideTakesPrecedenceEvenAtZero() {
        Ceremony ceremony = ceremony();
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID)).willReturn(Member.builder().role(MemberRole.OWNER).build());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(Organization.builder().build()));
        given(ceremonyMarginOverrideRepository.findByCeremonyId(CEREMONY_ID)).willReturn(Optional.of(
                CeremonyMarginOverride.builder().ceremony(ceremony).margin(new MarginInfo(DiscountType.PERCENT, BigDecimal.ZERO)).build()));
        var result = customerQuoteService.retrieveEffectiveMargin(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID);
        assertThat(result.getSource()).isEqualTo("CEREMONY_OVERRIDE");
        assertThat(result.getMarginValue()).isEqualByComparingTo("0");
        verify(organizationMarginPolicyRepository, never()).findEffective(any(), any());
    }

    @Test
    void quoteWithoutEffectiveMarginIsRejected() {
        Ceremony ceremony = ceremony();
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID)).willReturn(Member.builder().role(MemberRole.OWNER).build());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(Organization.builder().build()));
        assertThatThrownBy(() -> customerQuoteService.generateCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID,
                new CustomerQuoteDto.Request.GenerateQuote(List.of())))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(CeremonyErrorCode.MARGIN_NOT_SET);
        verify(customerQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용중지(또는 가격 기간 공백)된 단위 상품은 견적에 담을 수 없다")
    void generateCustomerQuote_withInactiveUnitProduct_rejected() {
        Ceremony ceremony = ceremony();
        stubCommon(ceremony);
        UnitProduct tablet = unitProduct(901L, "태블릿", UnitProductCategory.EQUIPMENT, null);
        given(unitProductRepository.findById(901L)).willReturn(Optional.of(tablet));
        willThrow(new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_INACTIVE))
                .given(ceremonyService).resolveSellableUnitProductPeriod(eq(tablet), any(LocalDate.class));

        CustomerQuoteDto.Request.GenerateQuote request = new CustomerQuoteDto.Request.GenerateQuote(
                List.of(new CustomerQuoteDto.Request.EquipmentPersonnelLine(901L, "태블릿", 1, BigDecimal.valueOf(10000)))
        );

        assertThatThrownBy(() -> customerQuoteService.generateCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_INACTIVE);
        verify(customerQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("배타 그룹이 겹치는 장비/인력 품목을 한 견적에 나란히 담을 수 없다")
    void generateCustomerQuote_withExclusivityGroupConflict_rejected() {
        Ceremony ceremony = ceremony();
        stubCommon(ceremony);
        UnitProduct near = unitProduct(902L, "근거리 현장지원", UnitProductCategory.PERSONNEL, "ONSITE_SUPPORT_TIER");
        UnitProduct far = unitProduct(903L, "원거리 현장지원", UnitProductCategory.PERSONNEL, "ONSITE_SUPPORT_TIER");
        given(unitProductRepository.findById(902L)).willReturn(Optional.of(near));
        given(unitProductRepository.findById(903L)).willReturn(Optional.of(far));
        willThrow(new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_GROUP_CONFLICT))
                .given(ceremonyService).checkExclusivityGroups(List.of(near, far));

        CustomerQuoteDto.Request.GenerateQuote request = new CustomerQuoteDto.Request.GenerateQuote(List.of(
                new CustomerQuoteDto.Request.EquipmentPersonnelLine(902L, "근거리 현장지원", 1, BigDecimal.valueOf(50000)),
                new CustomerQuoteDto.Request.EquipmentPersonnelLine(903L, "원거리 현장지원", 1, BigDecimal.valueOf(80000))
        ));

        assertThatThrownBy(() -> customerQuoteService.generateCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_GROUP_CONFLICT);
        verify(customerQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 장비/인력 품목은 두 검사를 통과해 견적에 담긴다")
    void generateCustomerQuote_withValidEquipment_succeeds() {
        Ceremony ceremony = ceremony();
        stubCommon(ceremony);
        UnitProduct tablet = unitProduct(901L, "태블릿", UnitProductCategory.EQUIPMENT, null);
        given(unitProductRepository.findById(901L)).willReturn(Optional.of(tablet));
        given(customerQuoteRepository.findMaxVersion(CEREMONY_ID)).willReturn(0);
        given(customerQuoteRepository.findByIdAndCeremonyId(any(), eq(CEREMONY_ID)))
                .willAnswer(invocation -> Optional.of(
                        CustomerQuote.builder()
                                .ceremony(ceremony).version(1)
                                .currencyCode("KRW").currencyFractionDigits((short) 0).currencyRoundingMode("HALF_UP")
                                .systemUsageCostAmount(BigDecimal.ZERO)
                                .margin(new MarginInfo(DiscountType.PERCENT, BigDecimal.TEN))
                                .systemUsageMarginAmount(BigDecimal.ZERO)
                                .systemUsageCustomerAmount(BigDecimal.ZERO)
                                .equipmentPersonnelCustomerAmount(BigDecimal.valueOf(10000))
                                .totalCustomerAmount(BigDecimal.valueOf(10000))
                                .build()
                ));

        CustomerQuoteDto.Request.GenerateQuote request = new CustomerQuoteDto.Request.GenerateQuote(
                List.of(new CustomerQuoteDto.Request.EquipmentPersonnelLine(901L, "태블릿", 1, BigDecimal.valueOf(10000)))
        );

        CustomerQuoteDto.Response.QuoteDetail result =
                customerQuoteService.generateCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID, request);

        assertThat(result).isNotNull();
        ArgumentCaptor<List<UnitProduct>> captor = ArgumentCaptor.forClass(List.class);
        verify(ceremonyService).checkExclusivityGroups(captor.capture());
        assertThat(captor.getValue()).containsExactly(tablet);
        verify(ceremonyService).resolveSellableUnitProductPeriod(eq(tablet), any(LocalDate.class));
        verify(customerQuoteRepository).save(any());
    }

    @Test
    @DisplayName("unitProductId 없는 줄(자유 품목)은 카탈로그 검증 없이 그대로 담긴다")
    void generateCustomerQuote_withFreeformItem_skipsCatalogValidationAndSaves() {
        Ceremony ceremony = ceremony();
        stubCommon(ceremony);
        given(customerQuoteRepository.findMaxVersion(CEREMONY_ID)).willReturn(0);
        given(customerQuoteRepository.findByIdAndCeremonyId(any(), eq(CEREMONY_ID)))
                .willAnswer(invocation -> Optional.of(
                        CustomerQuote.builder()
                                .ceremony(ceremony).version(1)
                                .currencyCode("KRW").currencyFractionDigits((short) 0).currencyRoundingMode("HALF_UP")
                                .systemUsageCostAmount(BigDecimal.ZERO)
                                .margin(new MarginInfo(DiscountType.PERCENT, BigDecimal.TEN))
                                .systemUsageMarginAmount(BigDecimal.ZERO)
                                .systemUsageCustomerAmount(BigDecimal.ZERO)
                                .equipmentPersonnelCustomerAmount(BigDecimal.valueOf(15000))
                                .totalCustomerAmount(BigDecimal.valueOf(15000))
                                .build()
                ));

        CustomerQuoteDto.Request.GenerateQuote request = new CustomerQuoteDto.Request.GenerateQuote(
                List.of(new CustomerQuoteDto.Request.EquipmentPersonnelLine(null, "태블릿 받침대", 1, BigDecimal.valueOf(15000)))
        );

        CustomerQuoteDto.Response.QuoteDetail result =
                customerQuoteService.generateCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID, request);

        assertThat(result).isNotNull();
        verify(unitProductRepository, never()).findById(any());
        verify(ceremonyService, never()).resolveSellableUnitProductPeriod(any(), any(LocalDate.class));
        ArgumentCaptor<List<UnitProduct>> captor = ArgumentCaptor.forClass(List.class);
        verify(ceremonyService).checkExclusivityGroups(captor.capture());
        assertThat(captor.getValue()).isEmpty();
        verify(customerQuoteRepository).save(any());
    }

    @Test
    @DisplayName("미리보기는 같은 계산을 하지만 저장하지 않는다(2026-09-12 사용자 요청 — 생성 버튼은 미리보기, 저장 버튼이 눌러야 저장)")
    void previewCustomerQuote_computesButDoesNotSave() {
        Ceremony ceremony = ceremony();
        stubCommon(ceremony);
        given(customerQuoteRepository.findMaxVersion(CEREMONY_ID)).willReturn(2);

        CustomerQuoteDto.Request.GenerateQuote request = new CustomerQuoteDto.Request.GenerateQuote(
                List.of(new CustomerQuoteDto.Request.EquipmentPersonnelLine(null, "태블릿 받침대", 2, BigDecimal.valueOf(5000)))
        );

        CustomerQuoteDto.Response.QuoteDetail result =
                customerQuoteService.previewCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID, request);

        assertThat(result.getSummary().getId()).isNull();
        assertThat(result.getSummary().getCreatedByLoginId()).isNull();
        assertThat(result.getSummary().getCreatedAt()).isNull();
        assertThat(result.getSummary().getVersion()).isEqualTo(3);
        assertThat(result.getSummary().getEquipmentPersonnelCustomerAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(result.getSummary().getTotalCustomerAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(result.getLines()).hasSize(2);
        verify(customerQuoteRepository, never()).save(any());
        verify(customerQuoteLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("견적서 삭제 — 줄을 먼저 지우고 헤더를 지운다")
    void deleteCustomerQuote_deletesLinesThenHeader() {
        Ceremony ceremony = ceremony();
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID))
                .willReturn(Member.builder().role(MemberRole.OWNER).build());
        CustomerQuote quote = CustomerQuote.builder()
                .ceremony(ceremony).version(1)
                .currencyCode("KRW").currencyFractionDigits((short) 0).currencyRoundingMode("HALF_UP")
                .systemUsageCostAmount(BigDecimal.ZERO)
                .margin(new MarginInfo(DiscountType.PERCENT, BigDecimal.TEN))
                .systemUsageMarginAmount(BigDecimal.ZERO)
                .systemUsageCustomerAmount(BigDecimal.ZERO)
                .equipmentPersonnelCustomerAmount(BigDecimal.ZERO)
                .totalCustomerAmount(BigDecimal.ZERO)
                .build();
        ReflectionTestUtils.setField(quote, "id", 500L);
        given(customerQuoteRepository.findByIdAndCeremonyId(500L, CEREMONY_ID)).willReturn(Optional.of(quote));

        customerQuoteService.deleteCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, 500L, CURRENT_USER_ID);

        verify(customerQuoteLineRepository).deleteAllByCustomerQuoteId(500L);
        verify(customerQuoteRepository).delete(quote);
    }

    @Test
    @DisplayName("견적서 삭제 — 없는 견적서면 거부된다")
    void deleteCustomerQuote_notFound_rejected() {
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony());
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID))
                .willReturn(Member.builder().role(MemberRole.OWNER).build());
        given(customerQuoteRepository.findByIdAndCeremonyId(999L, CEREMONY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerQuoteService.deleteCustomerQuote(ORGANIZATION_ID, CEREMONY_ID, 999L, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CUSTOMER_QUOTE_NOT_FOUND);
        verify(customerQuoteLineRepository, never()).deleteAllByCustomerQuoteId(any());
        verify(customerQuoteRepository, never()).delete(any(CustomerQuote.class));
    }
}
