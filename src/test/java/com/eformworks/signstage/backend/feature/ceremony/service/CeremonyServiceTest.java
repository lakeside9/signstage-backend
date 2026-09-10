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
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
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
 * {@link CeremonyService}의 과금 관련 로직 단위 테스트 — signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md(2026-09-10, 2단계) 재설계
 * 이후의 모델을 검증한다. 조직×플랜 할인 오버라이드가 Ceremony 생성 시점에 스냅샷 컬럼으로
 * 올바르게 반영되는지, 단위 상품 추가구매(장바구니형)가 한 헤더 아래 여러 줄로 저장되는지,
 * 유효 한도 계산이 승인된 구매 줄만 반영하는지를 다룬다. {@link OrganizationDiscountService}는
 * 이 테스트에서 목(mock) 처리하고, "그 결과를 CeremonyService가 올바른 스냅샷 컬럼에 옮겨
 * 담는지"만 검증한다 — 오버라이드 자체의 해석 로직은 {@link OrganizationDiscountServiceTest}가
 * 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyServiceTest {

    @Mock
    private CeremonyRepository ceremonyRepository;
    @Mock
    private CeremonyAssignmentRepository ceremonyAssignmentRepository;
    @Mock
    private CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;
    @Mock
    private CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    @Mock
    private CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    @Mock
    private CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    @Mock
    private CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    @Mock
    private BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BillingPlanRepository billingPlanRepository;
    @Mock
    private BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;
    @Mock
    private UnitProductRepository unitProductRepository;
    @Mock
    private UnitProductPricePeriodRepository unitProductPricePeriodRepository;
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
    @Mock
    private OrganizationSubscriptionService organizationSubscriptionService;

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

    private UnitProduct unitProduct(Long id, UnitProductType type) {
        UnitProduct unitProduct = UnitProduct.builder()
                .type(type).name(type.name()).category(UnitProductCategory.ESSENTIAL).build();
        ReflectionTestUtils.setField(unitProduct, "id", id);
        return unitProduct;
    }

    private UnitProductPricePeriod unitProductPeriod(UnitProduct unitProduct, BigDecimal supplyPrice, BigDecimal salePrice) {
        return UnitProductPricePeriod.builder()
                .unitProduct(unitProduct)
                .supplyPrice(supplyPrice).salePrice(salePrice)
                .active(true).effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    private BillingPlanDiscountPeriod planDiscountPeriod(BillingPlan plan, DiscountType discountType, BigDecimal discountValue) {
        return BillingPlanDiscountPeriod.builder()
                .billingPlan(plan)
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

        UnitProduct signers = unitProduct(901L, UnitProductType.SIGNERS);
        BillingPlanUnitProduct line = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(signers).includedQuantity(1).purchasable(false).build();

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(10L)).willReturn(Optional.empty());
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(line));
        given(unitProductPricePeriodRepository.findEffective(eq(901L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(signers, new BigDecimal("10000"), new BigDecimal("10005"))));
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, BigDecimal.ZERO)));
        given(ceremonyUnitProductPurchaseLineRepository.findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(10L)).willReturn(List.of());
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
        // 이 플랜은 포함 단위 상품이 없다고 가정한다 — 통화 검증(resolvePlanCurrency)이
        // 자연히 건너뛰어지고, recordPlanHistory의 스냅샷 루프도 빈 채로 끝난다.
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of());
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, new BigDecimal("10000"))));

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
    @DisplayName("단위 상품 추가구매는 여러 줄을 한 번에 담아 하나의 요청 헤더 아래 저장한다(장바구니형)")
    void purchaseUnitProducts_multipleLines_savesOneHeaderWithLines() {
        // given
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct signers = unitProduct(201L, UnitProductType.SIGNERS);
        UnitProduct tablets = unitProduct(202L, UnitProductType.TABLETS);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(201L)).willReturn(Optional.of(signers));
        given(unitProductRepository.findById(202L)).willReturn(Optional.of(tablets));
        given(unitProductPricePeriodRepository.findEffective(eq(201L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(signers, new BigDecimal("8000"), new BigDecimal("10000"))));
        given(unitProductPricePeriodRepository.findEffective(eq(202L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(tablets, new BigDecimal("40000"), new BigDecimal("50000"))));
        given(ceremonyUnitProductPurchaseRepository.save(any(CeremonyUnitProductPurchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyUnitProductPurchaseLineRepository.save(any(CeremonyUnitProductPurchaseLine.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CeremonyDto.Request.PurchaseUnitProducts request = new CeremonyDto.Request.PurchaseUnitProducts(List.of(
                new CeremonyDto.Request.PurchaseUnitProductLine(201L, 10),
                new CeremonyDto.Request.PurchaseUnitProductLine(202L, 5)
        ));

        // when
        CeremonyDto.Response.UnitProductPurchaseSummary result =
                ceremonyService.purchaseUnitProducts(ORGANIZATION_ID, 10L, CURRENT_USER_ID, request);

        // then — 옛 "묶음 상품"(secondaryCapacityType)이 하던 역할을 이제 한 요청의 여러 줄이 대신한다.
        assertThat(result.getLines()).hasSize(2);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(ceremonyUnitProductPurchaseRepository).save(any(CeremonyUnitProductPurchase.class));
        verify(ceremonyUnitProductPurchaseLineRepository, org.mockito.Mockito.times(2)).save(any(CeremonyUnitProductPurchaseLine.class));
    }

    @Test
    @DisplayName("이벤트 효과 묶음(토글형)은 수량 2 이상을 요청하면 거부된다")
    void purchaseUnitProducts_effectBundleQuantityOverOne_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct bundle = unitProduct(301L, UnitProductType.EVENT_EFFECT_BUNDLE);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductPurchaseRepository.save(any(CeremonyUnitProductPurchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(unitProductRepository.findById(301L)).willReturn(Optional.of(bundle));
        given(unitProductPricePeriodRepository.findEffective(eq(301L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(bundle, new BigDecimal("50000"), new BigDecimal("60000"))));

        CeremonyDto.Request.PurchaseUnitProducts request =
                new CeremonyDto.Request.PurchaseUnitProducts(List.of(new CeremonyDto.Request.PurchaseUnitProductLine(301L, 2)));

        assertThatThrownBy(() -> ceremonyService.purchaseUnitProducts(ORGANIZATION_ID, 10L, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
        verify(ceremonyUnitProductPurchaseLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("유효 한도는 플랜 기본값(스냅샷) + 승인된 구매 줄의 수량 합이다 — 묶음 상품의 보조 용량 개념은 폐지됐다")
    void calculateEffectiveCapacity_sumsApprovedPurchaseLines() {
        // given
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);

        given(ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(10L)).willReturn(Optional.empty());
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of());
        given(ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdAndUnitProduct_TypeAndPurchase_Status(10L, UnitProductType.TABLETS, PurchaseStatus.APPROVED))
                .willReturn(List.of(
                        purchaseLine(unitProduct(401L, UnitProductType.TABLETS), 10),
                        purchaseLine(unitProduct(401L, UnitProductType.TABLETS), 5)
                ));

        // when
        int effective = ceremonyService.calculateEffectiveCapacity(ceremony, UnitProductType.TABLETS);

        // then — 플랜 기본 포함 0(TABLETS는 플랜 기본값 개념이 없다) + 승인된 구매 줄 합(10+5)
        assertThat(effective).isEqualTo(15);
    }

    private CeremonyUnitProductPurchaseLine purchaseLine(UnitProduct unitProduct, int quantity) {
        CeremonyUnitProductPurchase header = CeremonyUnitProductPurchase.builder().build();
        ReflectionTestUtils.setField(header, "status", PurchaseStatus.APPROVED);
        return CeremonyUnitProductPurchaseLine.builder()
                .purchase(header)
                .unitProduct(unitProduct)
                .quantity(quantity)
                .currencyCode("KRW")
                .purchasedName(unitProduct.getName())
                .purchasedSalePrice(new BigDecimal("10000"))
                .purchasedTaxCode("KR_VAT_STANDARD")
                .build();
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
