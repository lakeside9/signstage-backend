package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyCapacityPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOptionalFeaturePurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeaturePricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanCapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanCapacityRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyCapacityPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOptionalFeaturePurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryCapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryCapacityRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeaturePricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 조직×품목 할인 오버라이드가 Ceremony 생성(플랜)/구매 요청(선택옵션·용량 추가구매) 시점에
 * 스냅샷 컬럼으로 올바르게 반영되는지 검증한다. signstage-docs
 * business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토) 참고 —
 * {@link OrganizationDiscountService}는 이 테스트에서 목(mock) 처리하고, "그 결과를
 * CeremonyService가 올바른 스냅샷 컬럼에 옮겨 담는지"만 검증한다. 오버라이드 자체의 해석
 * 로직(있으면 오버라이드, 없으면 카탈로그 값)은 {@link OrganizationDiscountServiceTest}가
 * 검증한다. 카탈로그 가격/사용여부는 이제 {@code *PricePeriod} 기간에서 나오므로(signstage-docs
 * business/billing-catalog-price-validity-period-review.md 결정, 2026-09-09), 각 시나리오는
 * {@code findEffective}가 그 기간을 돌려주도록 목을 세팅한다.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyServiceTest {

    @Mock
    private CeremonyRepository ceremonyRepository;
    @Mock
    private CeremonyAssignmentRepository ceremonyAssignmentRepository;
    @Mock
    private CeremonyCapacityPurchaseRepository ceremonyCapacityPurchaseRepository;
    @Mock
    private CeremonyOptionalFeaturePurchaseRepository ceremonyOptionalFeaturePurchaseRepository;
    @Mock
    private CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    @Mock
    private CeremonyPlanHistoryOptionalFeatureRepository ceremonyPlanHistoryOptionalFeatureRepository;
    @Mock
    private CeremonyPlanHistoryCapacityAddOnRepository ceremonyPlanHistoryCapacityAddOnRepository;
    @Mock
    private CeremonyPlanHistoryCapacityRepository ceremonyPlanHistoryCapacityRepository;
    @Mock
    private BillingPlanCapacityRepository billingPlanCapacityRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BillingPlanRepository billingPlanRepository;
    @Mock
    private BillingPlanPricePeriodRepository billingPlanPricePeriodRepository;
    @Mock
    private BillingPlanOptionalFeatureRepository billingPlanOptionalFeatureRepository;
    @Mock
    private BillingPlanCapacityAddOnRepository billingPlanCapacityAddOnRepository;
    @Mock
    private CapacityAddOnRepository capacityAddOnRepository;
    @Mock
    private CapacityAddOnPricePeriodRepository capacityAddOnPricePeriodRepository;
    @Mock
    private OptionalFeatureRepository optionalFeatureRepository;
    @Mock
    private OptionalFeaturePricePeriodRepository optionalFeaturePricePeriodRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    @Mock
    private OrganizationDiscountService organizationDiscountService;
    @Spy
    private MoneyCalculator moneyCalculator = new MoneyCalculator();
    @Mock
    private TaxPolicyResolver taxPolicyResolver;
    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private CeremonyService ceremonyService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CURRENT_USER_ID = 1L;

    @BeforeEach
    void setUpPermissions() {
        // 이 테스트 파일의 시나리오는 전부 OWNER라 생성/관리 액션이 항상 허용된다고 가정한다 —
        // 권한 자체의 허용/거부 판단은 RolePermissionServiceTest가 검증한다.
        lenient().when(rolePermissionService.isAllowed(eq("OWNER"), anyString())).thenReturn(true);
    }

    private Organization organization() {
        Organization organization = Organization.builder().name("조직").code("ORG1").build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);
        return organization;
    }

    private Ceremony ceremony(Organization organization, Long id) {
        Ceremony ceremony = Ceremony.builder().organization(organization).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", id);
        return ceremony;
    }

    private BillingPlanPricePeriod planPeriod(
            BillingPlan plan, BigDecimal supplyPrice, BigDecimal salePrice, DiscountType discountType, BigDecimal discountValue
    ) {
        return BillingPlanPricePeriod.builder()
                .billingPlan(plan)
                .supplyPrice(supplyPrice).salePrice(salePrice)
                .discountType(discountType).discountValue(discountValue)
                .active(true).effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    private CapacityAddOnPricePeriod addOnPeriod(
            CapacityAddOn addOn, BigDecimal supplyPrice, BigDecimal salePrice, DiscountType discountType, BigDecimal discountValue
    ) {
        return CapacityAddOnPricePeriod.builder()
                .capacityAddOn(addOn)
                .supplyPrice(supplyPrice).salePrice(salePrice)
                .discountType(discountType).discountValue(discountValue)
                .active(true).effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    private OptionalFeaturePricePeriod featurePeriod(
            OptionalFeature feature, BigDecimal supplyPrice, BigDecimal salePrice, DiscountType discountType, BigDecimal discountValue
    ) {
        return OptionalFeaturePricePeriod.builder()
                .optionalFeature(feature)
                .supplyPrice(supplyPrice).salePrice(salePrice)
                .discountType(discountType).discountValue(discountValue)
                .active(true).effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Test
    @DisplayName("KRW 예상 청구액은 통화 0자리 반올림 후 유효 세금 정책의 VAT를 합산한다")
    void calculateEstimatedTotal_appliesCurrencyRoundingAndTaxPolicy() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("기본").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();
        TaxPolicy taxPolicy = mock(TaxPolicy.class);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(10L)).willReturn(Optional.empty());
        given(billingPlanPricePeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planPeriod(plan, new BigDecimal("10000"), new BigDecimal("10005"), DiscountType.FIXED_AMOUNT, BigDecimal.ZERO)));
        given(ceremonyCapacityPurchaseRepository.findAllByCeremonyIdOrderByCreatedAtDesc(10L)).willReturn(List.of());
        given(ceremonyOptionalFeaturePurchaseRepository.findAllByCeremonyIdOrderByCreatedAtDesc(10L)).willReturn(List.of());
        given(taxPolicyResolver.resolve(any(), any(), any())).willReturn(taxPolicy);
        given(taxPolicy.getRatePercent()).willReturn(new BigDecimal("10.0000"));

        CeremonyDto.Response.EstimatedTotal result =
                ceremonyService.calculateEstimatedTotal(ORGANIZATION_ID, 10L, CURRENT_USER_ID);

        assertThat(result.getNetAmount()).isEqualByComparingTo("10005");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("1001");
        assertThat(result.getGrossAmount()).isEqualByComparingTo("11006");
        assertThat(result.getCurrencyCode()).isEqualTo("KRW");
        assertThat(result.getFractionDigits()).isZero();
    }

    @Test
    @DisplayName("Ceremony 생성 시 조직×플랜 할인 오버라이드가 있으면 카탈로그 값 대신 그 값을 CeremonyPlanHistory에 스냅샷한다")
    void createCeremony_withPlanDiscountOverride_snapshotsOverride() {
        // given
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Member member = Member.builder().role(MemberRole.OWNER).build();
        User creator = User.builder().loginId("user1").name("사용자1").build();

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(billingPlanRepository.findById(101L)).willReturn(Optional.of(plan));
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(creator));
        given(billingPlanPricePeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planPeriod(
                        plan, new BigDecimal("100000"), new BigDecimal("90000"), DiscountType.FIXED_AMOUNT, new BigDecimal("10000")
                )));

        OrganizationDiscountService.EffectiveDiscount overrideDiscount =
                new OrganizationDiscountService.EffectiveDiscount(DiscountType.PERCENT, new BigDecimal("30"));
        given(organizationDiscountService.resolveBillingPlanDiscount(eq(organization), eq(101L), any(), any(), any(LocalDate.class)))
                .willReturn(overrideDiscount);

        CeremonyDto.Request.CreateCeremony request = new CeremonyDto.Request.CreateCeremony(101L, "행사1");

        // when
        ceremonyService.createCeremony(ORGANIZATION_ID, CURRENT_USER_ID, request);

        // then — 카탈로그 자체 할인(FIXED_AMOUNT 10000)이 아니라 오버라이드(PERCENT 30)가 스냅샷됐는지 확인
        ArgumentCaptor<CeremonyPlanHistory> captor = ArgumentCaptor.forClass(CeremonyPlanHistory.class);
        verify(ceremonyPlanHistoryRepository).save(captor.capture());
        CeremonyPlanHistory history = captor.getValue();
        assertThat(history.getPlanDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(history.getPlanDiscountValue()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("조직×용량추가구매 할인 오버라이드가 있으면 카탈로그 값 대신 그 값을 CeremonyCapacityPurchase에 스냅샷한다")
    void purchaseCapacity_withDiscountOverride_snapshotsOverride() {
        // given
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();

        CapacityAddOn addOn = CapacityAddOn.builder().capacityType(CapacityType.SIGNERS).unitAmount(10).build();
        ReflectionTestUtils.setField(addOn, "id", 201L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(capacityAddOnRepository.findById(201L)).willReturn(Optional.of(addOn));
        given(capacityAddOnPricePeriodRepository.findEffective(eq(201L), any(LocalDate.class)))
                .willReturn(Optional.of(addOnPeriod(
                        addOn, new BigDecimal("50000"), new BigDecimal("45000"), DiscountType.FIXED_AMOUNT, new BigDecimal("5000")
                )));

        OrganizationDiscountService.EffectiveDiscount overrideDiscount =
                new OrganizationDiscountService.EffectiveDiscount(DiscountType.PERCENT, new BigDecimal("20"));
        given(organizationDiscountService.resolveCapacityAddOnDiscount(eq(organization), eq(201L), any(), any(), any(LocalDate.class)))
                .willReturn(overrideDiscount);

        CeremonyDto.Request.PurchaseCapacity request = new CeremonyDto.Request.PurchaseCapacity(201L, 2);

        // when
        ceremonyService.purchaseCapacity(ORGANIZATION_ID, 10L, CURRENT_USER_ID, request);

        // then
        ArgumentCaptor<CeremonyCapacityPurchase> captor = ArgumentCaptor.forClass(CeremonyCapacityPurchase.class);
        verify(ceremonyCapacityPurchaseRepository).save(captor.capture());
        CeremonyCapacityPurchase purchase = captor.getValue();
        assertThat(purchase.getPurchasedDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(purchase.getPurchasedDiscountValue()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("묶음 상품(예: 서명자+태블릿) 구매는 보조 용량 단가도 함께 스냅샷한다")
    void purchaseCapacity_comboProduct_snapshotsSecondaryUnitAmount() {
        // given
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();

        CapacityAddOn comboAddOn = CapacityAddOn.builder()
                .capacityType(CapacityType.SIGNERS)
                .unitAmount(10)
                .secondaryCapacityType(CapacityType.TABLETS)
                .secondaryUnitAmount(10)
                .build();
        ReflectionTestUtils.setField(comboAddOn, "id", 301L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(capacityAddOnRepository.findById(301L)).willReturn(Optional.of(comboAddOn));
        given(capacityAddOnPricePeriodRepository.findEffective(eq(301L), any(LocalDate.class)))
                .willReturn(Optional.of(addOnPeriod(
                        comboAddOn, new BigDecimal("100000"), new BigDecimal("90000"), DiscountType.FIXED_AMOUNT, BigDecimal.ZERO
                )));
        given(organizationDiscountService.resolveCapacityAddOnDiscount(eq(organization), eq(301L), any(), any(), any(LocalDate.class)))
                .willReturn(new OrganizationDiscountService.EffectiveDiscount(DiscountType.FIXED_AMOUNT, BigDecimal.ZERO));

        CeremonyDto.Request.PurchaseCapacity request = new CeremonyDto.Request.PurchaseCapacity(301L, 2);

        // when
        ceremonyService.purchaseCapacity(ORGANIZATION_ID, 10L, CURRENT_USER_ID, request);

        // then
        ArgumentCaptor<CeremonyCapacityPurchase> captor = ArgumentCaptor.forClass(CeremonyCapacityPurchase.class);
        verify(ceremonyCapacityPurchaseRepository).save(captor.capture());
        CeremonyCapacityPurchase purchase = captor.getValue();
        assertThat(purchase.getPurchasedUnitAmount()).isEqualTo(10);
        assertThat(purchase.getPurchasedSecondaryUnitAmount()).isEqualTo(10);
    }

    @Test
    @DisplayName("묶음 상품(예: 서명자+태블릿) 구매는 보조 용량 쪽 유효 한도에도 반영된다")
    void calculateEffectiveCapacity_comboPurchase_addsToSecondaryCapacityType() {
        // given
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);

        CapacityAddOn comboAddOn = CapacityAddOn.builder()
                .capacityType(CapacityType.SIGNERS)
                .unitAmount(10)
                .secondaryCapacityType(CapacityType.TABLETS)
                .secondaryUnitAmount(10)
                .build();
        ReflectionTestUtils.setField(comboAddOn, "id", 301L);

        CeremonyCapacityPurchase comboPurchase = CeremonyCapacityPurchase.builder()
                .ceremony(ceremony)
                .capacityAddOn(comboAddOn)
                .quantity(2)
                .currencyCode("KRW")
                .purchasedUnitAmount(10)
                .purchasedSecondaryUnitAmount(10)
                .purchasedSalePrice(new BigDecimal("90000"))
                .purchasedDiscountType(DiscountType.FIXED_AMOUNT)
                .purchasedDiscountValue(BigDecimal.ZERO)
                .purchasedTaxCode("KR_VAT_STANDARD")
                .build();

        given(ceremonyCapacityPurchaseRepository
                .findAllByCeremonyIdAndCapacityAddOn_SecondaryCapacityTypeAndStatus(10L, CapacityType.TABLETS, PurchaseStatus.APPROVED))
                .willReturn(List.of(comboPurchase));

        // when — 플랜 기본 포함 0(TABLETS는 플랜 기본값 개념이 없다) + 묶음 구매(수량 2 × 보조 단가 10)
        int effective = ceremonyService.calculateEffectiveCapacity(ceremony, CapacityType.TABLETS);

        // then
        assertThat(effective).isEqualTo(20);
    }

    @Test
    @DisplayName("조직×선택옵션 할인 오버라이드가 없으면 카탈로그 자체의 할인값을 그대로 CeremonyOptionalFeaturePurchase에 스냅샷한다")
    void purchaseOptionalFeature_withoutDiscountOverride_snapshotsCatalogValue() {
        // given
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();

        OptionalFeature feature = OptionalFeature.builder()
                .code(OptionalFeatureCode.SIGNER_FIELD_ZOOM)
                .name("서명 하이라이트")
                .build();
        ReflectionTestUtils.setField(feature, "id", 301L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(optionalFeatureRepository.findById(301L)).willReturn(Optional.of(feature));
        given(optionalFeaturePricePeriodRepository.findEffective(eq(301L), any(LocalDate.class)))
                .willReturn(Optional.of(featurePeriod(
                        feature, new BigDecimal("30000"), new BigDecimal("27000"), DiscountType.FIXED_AMOUNT, new BigDecimal("3000")
                )));

        // 오버라이드 없음 — OrganizationDiscountService가 카탈로그 값을 그대로 돌려주는 상황을 흉내낸다.
        given(organizationDiscountService.resolveOptionalFeatureDiscount(eq(organization), eq(301L), any(), any(), any(LocalDate.class)))
                .willReturn(new OrganizationDiscountService.EffectiveDiscount(DiscountType.FIXED_AMOUNT, new BigDecimal("3000")));

        CeremonyDto.Request.PurchaseOptionalFeature request = new CeremonyDto.Request.PurchaseOptionalFeature(301L);

        // when
        ceremonyService.purchaseOptionalFeature(ORGANIZATION_ID, 10L, CURRENT_USER_ID, request);

        // then
        ArgumentCaptor<CeremonyOptionalFeaturePurchase> captor = ArgumentCaptor.forClass(CeremonyOptionalFeaturePurchase.class);
        verify(ceremonyOptionalFeaturePurchaseRepository).save(captor.capture());
        CeremonyOptionalFeaturePurchase purchase = captor.getValue();
        assertThat(purchase.getPurchasedDiscountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(purchase.getPurchasedDiscountValue()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("플랫폼 관리자용 행사 목록 조회는 조직 멤버십 없이도(memberRepository 조회 없이) 그 조직의 모든 행사를 본다")
    void findCeremoniesByPlatformAdmin_returnsCeremoniesWithoutMembershipCheck() {
        // given
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ceremony> page = new PageImpl<>(List.of(ceremony), pageable, 1);

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(ceremonyRepository.search(ORGANIZATION_ID, null, null, null, null, pageable)).willReturn(page);

        // when
        Page<CeremonyDto.Response.CeremonySummary> result =
                ceremonyService.findCeremoniesByPlatformAdmin(ORGANIZATION_ID, null, null, pageable);

        // then — memberRepository는 이 흐름에서 전혀 조회되지 않는다(플랫폼 관리자는 조직 멤버가 아니어도 된다).
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        verify(memberRepository, never()).findByOrganizationIdAndUserIdAndStatus(any(), any(), any());
    }

    // ---- 데모 조직 우회 — signstage-docs
    // business/demo-account-exhibition-signer-preview-review.md 11장(2026-09-09, 결정 번복) ----

    @Test
    @DisplayName("데모 조직 + ACTION_DEMO_CEREMONY_MANAGE 허용(PLATFORM_OPS 이상)이면 가상 OWNER 멤버를 돌려준다")
    void findActiveMemberOrThrow_demoOrganizationWithManagePermission_returnsVirtualOwner() {
        Organization demoOrganization = organization();
        ReflectionTestUtils.setField(demoOrganization, "demo", true);
        User platformAdminUser = User.builder().loginId("admin1").name("관리자1").platformRole(PlatformRole.PLATFORM_OPS).build();

        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, 99L, MemberStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(demoOrganization));
        given(userRepository.findById(99L)).willReturn(Optional.of(platformAdminUser));
        given(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_DEMO_CEREMONY_MANAGE")).willReturn(true);

        Member virtualMember = ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, 99L);

        assertThat(virtualMember.getRole()).isEqualTo(MemberRole.OWNER);
        assertThat(virtualMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("데모 조직이지만 ACTION_DEMO_CEREMONY_MANAGE가 없으면(PLATFORM_SUPPORT) 조회만 가능한 가상 VIEWER 멤버를 돌려준다")
    void findActiveMemberOrThrow_demoOrganizationWithoutManagePermission_returnsVirtualViewer() {
        Organization demoOrganization = organization();
        ReflectionTestUtils.setField(demoOrganization, "demo", true);
        User platformAdminUser = User.builder().loginId("admin2").name("관리자2").platformRole(PlatformRole.PLATFORM_SUPPORT).build();

        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, 98L, MemberStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(demoOrganization));
        given(userRepository.findById(98L)).willReturn(Optional.of(platformAdminUser));
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_DEMO_CEREMONY_MANAGE")).willReturn(false);

        Member virtualMember = ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, 98L);

        assertThat(virtualMember.getRole()).isEqualTo(MemberRole.VIEWER);
    }

    @Test
    @DisplayName("데모 조직이 아니면 플랫폼 관리자라도 우회되지 않고 접근이 거부된다")
    void findActiveMemberOrThrow_nonDemoOrganization_stillDenied() {
        Organization organization = organization();

        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, 99L, MemberStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));

        assertThatThrownBy(() -> ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, 99L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }
}
