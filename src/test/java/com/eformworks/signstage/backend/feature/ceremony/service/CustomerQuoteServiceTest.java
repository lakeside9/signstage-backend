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
        given(organizationMarginPolicyRepository.findByOrganizationId(ORGANIZATION_ID))
                .willReturn(Optional.of(OrganizationMarginPolicy.builder()
                        .margin(new MarginInfo(DiscountType.PERCENT, BigDecimal.TEN)).build()));
        given(ceremonyService.buildQuoteCalculation(ceremony))
                .willReturn(new CeremonyService.QuoteCalculation(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
                ));
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
}
