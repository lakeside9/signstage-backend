package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductCartLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.TaxCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductCartLineRepository;
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
    private CeremonyUnitProductCartLineRepository ceremonyUnitProductCartLineRepository;
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

    private CeremonyUnitProductCartLine cartLine(Ceremony ceremony, UnitProduct unitProduct, int quantity) {
        return CeremonyUnitProductCartLine.builder().ceremony(ceremony).unitProduct(unitProduct).quantity(quantity).build();
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
                .billingPlan(plan).unitProduct(signers).includedQuantity(1).build();

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        // ceremony가 DRAFT라 findLatestPlanHistoryForSnapshot이 스냅샷 조회 자체를 건너뛰고
        // 항상 라이브 값으로 폴백한다(2026-09-10) — ceremonyPlanHistoryRepository는 안 불린다.
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(line));
        given(unitProductPricePeriodRepository.findEffective(eq(901L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(signers, new BigDecimal("10000"), new BigDecimal("10005"))));
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, BigDecimal.ZERO)));
        given(ceremonyUnitProductPurchaseLineRepository.findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(10L)).willReturn(List.of());
        given(taxPolicyResolver.resolve(any(), any(), any())).willReturn(taxPolicy);
        given(taxPolicy.getRatePercent()).willReturn(new BigDecimal("10.0000"));
        given(taxPolicy.getCategory()).willReturn(TaxCategory.STANDARD);
        given(taxPolicy.getPriceInclusion()).willReturn("EXCLUSIVE");

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

    /**
     * 2026-09-11 사용자 요청 — signstage-docs
     * business/ceremony-plan-price-snapshot-consistency-review.md 3.1절(방어). 플랜에
     * 포함된 단위 상품 중 그 날짜에 유효한 가격 기간이 없으면(카탈로그 관리자가 가격 기간
     * 사이에 공백을 남긴 경우) 예전엔 조용히 0원으로 스냅샷됐지만, 이제 추가구매 경로와
     * 같은 기준으로 예외를 던져 플랜 선택 자체를 막는다.
     */
    @Test
    @DisplayName("플랜에 포함된 단위 상품 중 가격 공백(그 날짜에 유효한 기간 없음)이 있으면 플랜 선택 자체를 거부한다")
    void createCeremony_withUnitProductPriceGap_rejected() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct signers = unitProduct(201L, UnitProductType.SIGNERS);
        BillingPlanUnitProduct line = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(signers).includedQuantity(1).build();

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(billingPlanRepository.findById(101L)).willReturn(Optional.of(plan));
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(line));
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, BigDecimal.ZERO)));
        // 공백 — 이 단위 상품엔 오늘 유효한 가격 기간이 없다.
        given(unitProductPricePeriodRepository.findEffective(eq(201L), any(LocalDate.class))).willReturn(Optional.empty());
        given(organizationDiscountService.resolveBillingPlanDiscount(eq(organization), eq(101L), any(), any(), any(LocalDate.class)))
                .willReturn(new OrganizationDiscountService.EffectiveDiscount(DiscountType.FIXED_AMOUNT, BigDecimal.ZERO));

        CeremonyDto.Request.CreateCeremony request = new CeremonyDto.Request.CreateCeremony(101L, "행사1");

        // 실제로는 @Transactional이라 ceremonyRepository.save 자체는 먼저 불리고 예외로 롤백된다 —
        // 여기서는 순수하게 "예외가 나는가"만 확인한다.
        assertThatThrownBy(() -> ceremonyService.createCeremony(ORGANIZATION_ID, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_INACTIVE);
    }

    /**
     * 2026-09-11 사용자 요청 — signstage-docs
     * business/ceremony-plan-price-snapshot-consistency-review.md 3.2절. "플랜 확정"이
     * 상태만 바꾸던 것에서, 확정 직전 오늘 날짜로 스냅샷을 한 번 더 찍도록 바뀌었다 —
     * 확정 버튼을 누르는 순간 보이던 값과 실제로 고정되는 값을 일치시킨다.
     */
    @Test
    @DisplayName("플랜 확정은 상태 전이 전에 오늘 날짜로 플랜 이력을 한 번 더 스냅샷한다")
    void confirmPlan_recordsFreshPlanHistorySnapshot() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        stubOwnerMember(ceremony);

        // 이 플랜은 포함 단위 상품이 없다고 가정한다 — createCeremony_withPlanDiscountOverride_snapshotsOverride와 같은 이유.
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of());
        given(billingPlanDiscountPeriodRepository.findEffective(eq(101L), any(LocalDate.class)))
                .willReturn(Optional.of(planDiscountPeriod(plan, DiscountType.FIXED_AMOUNT, new BigDecimal("10000"))));
        given(organizationDiscountService.resolveBillingPlanDiscount(eq(organization), eq(101L), any(), any(), any(LocalDate.class)))
                .willReturn(new OrganizationDiscountService.EffectiveDiscount(DiscountType.FIXED_AMOUNT, new BigDecimal("10000")));

        ceremonyService.confirmPlan(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID);

        verify(ceremonyPlanHistoryRepository, org.mockito.Mockito.times(1)).save(any(CeremonyPlanHistory.class));
    }

    @Test
    @DisplayName("단위 상품 추가구매(구매하기)는 장바구니에 담긴 여러 줄을 한 번에 하나의 요청 헤더 아래 저장한다")
    void purchaseUnitProducts_multipleLines_savesOneHeaderWithLines() {
        // given
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        // 이 테스트가 의도하는 건 배포 전 레거시(plan 없음, IN_PROGRESS) 행사의 "안 A 큐레이션
        // 미적용" 동작이다 — DRAFT+플랜 없음(신규, 2026-09-10부터 가능)은 별도로 막힌다
        // (purchaseUnitProducts_rejectsWhenDraftWithoutPlanSelected 참고).
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct signers = unitProduct(201L, UnitProductType.SIGNERS);
        UnitProduct tablets = unitProduct(202L, UnitProductType.TABLETS);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L)).willReturn(List.of(
                cartLine(ceremony, signers, 10),
                cartLine(ceremony, tablets, 5)
        ));
        given(unitProductPricePeriodRepository.findEffective(eq(201L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(signers, new BigDecimal("8000"), new BigDecimal("10000"))));
        given(unitProductPricePeriodRepository.findEffective(eq(202L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(tablets, new BigDecimal("40000"), new BigDecimal("50000"))));
        given(ceremonyUnitProductPurchaseRepository.save(any(CeremonyUnitProductPurchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyUnitProductPurchaseLineRepository.save(any(CeremonyUnitProductPurchaseLine.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        CeremonyDto.Response.UnitProductPurchaseSummary result =
                ceremonyService.purchaseUnitProducts(ORGANIZATION_ID, 10L, CURRENT_USER_ID);

        // then — 옛 "묶음 상품"(secondaryCapacityType)이 하던 역할을 이제 한 요청의 여러 줄이 대신한다.
        // 두 상품 모두 기본 카테고리(ESSENTIAL, 시스템 사용료)라 자가-체크아웃으로 즉시 APPROVED된다.
        assertThat(result.getLines()).hasSize(2);
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(ceremonyUnitProductPurchaseRepository).save(any(CeremonyUnitProductPurchase.class));
        verify(ceremonyUnitProductPurchaseLineRepository, org.mockito.Mockito.times(2)).save(any(CeremonyUnitProductPurchaseLine.class));
        verify(ceremonyUnitProductCartLineRepository).deleteAllByCeremonyId(10L);
    }

    @Test
    @DisplayName("이벤트 효과 묶음(토글형)은 장바구니에 수량 2 이상으로 담겨 있으면 구매 시점에 거부된다")
    void purchaseUnitProducts_effectBundleQuantityOverOne_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        // 이 테스트가 의도하는 건 배포 전 레거시(plan 없음, IN_PROGRESS) 행사의 동작이다 —
        // purchaseUnitProducts_multipleLines_savesOneHeaderWithLines와 같은 이유.
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct bundle = unitProduct(301L, UnitProductType.EVENT_EFFECT_BUNDLE);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductPurchaseRepository.save(any(CeremonyUnitProductPurchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L))
                .willReturn(List.of(cartLine(ceremony, bundle, 2)));
        given(unitProductPricePeriodRepository.findEffective(eq(301L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(bundle, new BigDecimal("50000"), new BigDecimal("60000"))));

        assertThatThrownBy(() -> ceremonyService.purchaseUnitProducts(ORGANIZATION_ID, 10L, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID);
        verify(ceremonyUnitProductPurchaseLineRepository, never()).save(any());
        verify(ceremonyUnitProductCartLineRepository, never()).deleteAllByCeremonyId(any());
    }

    /**
     * 자가-체크아웃 범위 단위 테스트 — signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 2·4장 결정(2026-09-11):
     * 시스템 사용료(ESSENTIAL/APPLICATION)만 자가-체크아웃하고, 장비·인력(EQUIPMENT/
     * PERSONNEL)이 섞이면(배포 전 레거시 plan 없는 행사 한정 — 플랜이 있는 행사는 애초에
     * 이 카테고리가 구매 카탈로그에 없다) 예전처럼 PENDING으로 남긴다.
     */
    @Test
    @DisplayName("장바구니에 장비/인력 카테고리가 섞여 있으면 자가-체크아웃하지 않고 PENDING으로 남긴다")
    void purchaseUnitProducts_nonSystemUsageFeeLine_staysPending() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan(); // 레거시(plan 없음, IN_PROGRESS) 흉내 — 카탈로그 필터가 적용되지 않는 유일한 경로.
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct tablets = UnitProduct.builder()
                .type(UnitProductType.TABLETS).name("태블릿").category(UnitProductCategory.EQUIPMENT).build();
        ReflectionTestUtils.setField(tablets, "id", 401L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L))
                .willReturn(List.of(cartLine(ceremony, tablets, 3)));
        given(unitProductPricePeriodRepository.findEffective(eq(401L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(tablets, new BigDecimal("40000"), new BigDecimal("50000"))));
        given(ceremonyUnitProductPurchaseRepository.save(any(CeremonyUnitProductPurchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyUnitProductPurchaseLineRepository.save(any(CeremonyUnitProductPurchaseLine.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CeremonyDto.Response.UnitProductPurchaseSummary result =
                ceremonyService.purchaseUnitProducts(ORGANIZATION_ID, 10L, CURRENT_USER_ID);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(ceremonyUnitProductCartLineRepository).deleteAllByCeremonyId(10L);
    }

    @Test
    @DisplayName("장바구니 담기 — 같은 상품을 두 번 담으면 새 줄이 아니라 기존 줄의 수량에 더한다")
    void addToCart_sameItemTwice_mergesQuantity() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct signers = unitProduct(201L, UnitProductType.SIGNERS);
        CeremonyUnitProductCartLine existing = cartLine(ceremony, signers, 3);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(201L)).willReturn(Optional.of(signers));
        given(ceremonyUnitProductCartLineRepository.findByCeremonyIdAndUnitProductId(10L, 201L))
                .willReturn(Optional.of(existing));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L)).willReturn(List.of(existing));

        ceremonyService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(201L, 4));

        assertThat(existing.getQuantity()).isEqualTo(7);
        verify(ceremonyUnitProductCartLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 담기 — 이벤트 효과 묶음(토글형)을 수량 2 이상으로 담으려 하면 거부된다")
    void addToCart_eventEffectBundleQuantityOverOne_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 301L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(301L)).willReturn(Optional.of(bundle));

        assertThatThrownBy(() -> ceremonyService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(301L, 2)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID);
        verify(ceremonyUnitProductCartLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 담기 — 이미 PENDING/APPROVED로 구매된 이벤트 효과 묶음은 다시 담을 수 없다")
    void addToCart_eventEffectBundleAlreadyPurchased_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 301L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(301L)).willReturn(Optional.of(bundle));
        given(ceremonyUnitProductPurchaseLineRepository
                .existsByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(10L, 301L, List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)))
                .willReturn(true);

        assertThatThrownBy(() -> ceremonyService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(301L, 1)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_ALREADY_PURCHASED);
        verify(ceremonyUnitProductCartLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 수량 수정 — 이벤트 효과 묶음(토글형)을 2 이상으로 고치려 하면 거부된다")
    void updateCartLine_eventEffectBundleQuantityOverOne_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 301L);
        CeremonyUnitProductCartLine existing = cartLine(ceremony, bundle, 1);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductCartLineRepository.findByCeremonyIdAndUnitProductId(10L, 301L))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> ceremonyService.updateCartLine(
                ORGANIZATION_ID, 10L, CURRENT_USER_ID, 301L, new CeremonyDto.Request.UpdateCartLine(2)
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID);
        assertThat(existing.getQuantity()).isEqualTo(1);
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

        // ceremony가 DRAFT라 findLatestPlanHistoryForSnapshot이 스냅샷 조회 자체를 건너뛰고
        // 항상 라이브 값으로 폴백한다(2026-09-10) — ceremonyPlanHistoryRepository는 안 불린다.
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
        given(ceremonyRepository.search(ORGANIZATION_ID, null, null, null, null, null, pageable)).willReturn(page);

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

    /**
     * {@link CeremonyService#deleteCeremony}의 "플랜이 확정되지 않은(DRAFT) 행사만 삭제 가능"
     * 규칙 단위 테스트 — signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 11장, 2026-09-10 사용자
     * 요청.
     */
    private static final Long CEREMONY_ID = 10L;

    private void stubOwnerMember(Ceremony ceremony) {
        Member member = Member.builder().role(MemberRole.OWNER).build();
        given(ceremonyRepository.findById(CEREMONY_ID)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
    }

    @Test
    @DisplayName("삭제 — DRAFT이고 대기중/승인된 추가구매가 없으면 자신의 이력·추가구매·장바구니·배정을 함께 지운다")
    void deleteCeremony_deletesWhenDraftAndNoActivity() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        stubOwnerMember(ceremony);
        given(ceremonyUnitProductPurchaseRepository.existsByCeremonyIdAndStatusIn(
                CEREMONY_ID, List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)
        )).willReturn(false);

        ceremonyService.deleteCeremony(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID);

        verify(ceremonyPlanHistoryUnitProductRepository).deleteAllByCeremonyPlanHistory_CeremonyId(CEREMONY_ID);
        verify(ceremonyPlanHistoryRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyUnitProductPurchaseLineRepository).deleteAllByPurchase_CeremonyId(CEREMONY_ID);
        verify(ceremonyUnitProductPurchaseRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyUnitProductCartLineRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyAssignmentRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyRepository).delete(ceremony);
    }

    @Test
    @DisplayName("삭제 — 플랜이 확정된(IN_PROGRESS) 행사는 거부하고 아무것도 지우지 않는다")
    void deleteCeremony_rejectsWhenPlanConfirmed() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        ceremony.confirmPlan();
        stubOwnerMember(ceremony);

        assertThatThrownBy(() -> ceremonyService.deleteCeremony(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_NOT_DELETABLE);

        verify(ceremonyRepository, never()).delete(any(Ceremony.class));
    }

    @Test
    @DisplayName("삭제 — DRAFT이어도 대기중/승인된 추가구매가 있으면 거부한다")
    void deleteCeremony_rejectsWhenActivePurchaseExists() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        stubOwnerMember(ceremony);
        given(ceremonyUnitProductPurchaseRepository.existsByCeremonyIdAndStatusIn(
                CEREMONY_ID, List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)
        )).willReturn(true);

        assertThatThrownBy(() -> ceremonyService.deleteCeremony(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_NOT_DELETABLE);

        verify(ceremonyRepository, never()).delete(any(Ceremony.class));
    }

    /**
     * 행사 등록 시 플랜 선택을 나중으로 미루는 흐름(2026-09-10, 사용자 요청) 단위 테스트 —
     * signstage-docs business/ceremony-registration-flow-and-billing-tab-separation-review.md.
     */
    @Test
    @DisplayName("생성 — billingPlanId를 생략하면 플랜 없이 DRAFT로 만들고 플랜 이력을 남기지 않는다")
    void createCeremony_withoutBillingPlanId_createsDraftWithoutPlanHistory() {
        Organization organization = organization();
        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        Member member = Member.builder().role(MemberRole.OWNER).build();
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        User creator = User.builder().loginId("u1").name("사용자").build();
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(creator));

        CeremonyDto.Request.CreateCeremony request = new CeremonyDto.Request.CreateCeremony(null, "행사");

        CeremonyDto.Response.CeremonySummary result = ceremonyService.createCeremony(ORGANIZATION_ID, CURRENT_USER_ID, request);

        assertThat(result.getBillingPlanId()).isNull();
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(ceremonyPlanHistoryRepository, never()).save(any());
        verify(billingPlanRepository, never()).findById(any());
    }

    @Test
    @DisplayName("확정 — 플랜을 한 번도 선택하지 않았으면 거부한다")
    void confirmPlan_rejectsWhenNoPlanSelected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        stubOwnerMember(ceremony);

        assertThatThrownBy(() -> ceremonyService.confirmPlan(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_PLAN_NOT_SELECTED);
    }

    @Test
    @DisplayName("추가구매 — 플랜을 선택한 적 없는 신규 DRAFT 행사는 거부한다(안 A 큐레이션 구멍 방지)")
    void purchaseUnitProducts_rejectsWhenDraftWithoutPlanSelected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        stubOwnerMember(ceremony);

        // 플랜 확정 가드가 장바구니 조회보다 먼저 걸려야 한다 — 장바구니 리포지토리는
        // 아예 안 불려야 한다.
        assertThatThrownBy(() -> ceremonyService.purchaseUnitProducts(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        verify(ceremonyUnitProductCartLineRepository, never()).findAllByCeremonyIdOrderByIdAsc(any());
    }

    /**
     * 2026-09-11 사용자 요청 — "플랜 확정 후에 단위 상품 추가 구매를 할 수 있도록 제약을
     * 걸어주세요." 플랜을 고르기만 하고 아직 확정 전인 DRAFT 행사는 담긴 것과 무관하게
     * 거부된다 — {@code checkCeremonyPlanConfirmed}는 billingPlan 값이 아니라 status만 본다.
     */
    @Test
    @DisplayName("추가구매 — 플랜을 선택만 하고 아직 확정 전(DRAFT)인 행사는 거부한다")
    void purchaseUnitProducts_rejectsWhenPlanSelectedButNotConfirmed() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        stubOwnerMember(ceremony);

        assertThatThrownBy(() -> ceremonyService.purchaseUnitProducts(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        verify(ceremonyUnitProductCartLineRepository, never()).findAllByCeremonyIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("장바구니 담기 — 플랜을 선택만 하고 아직 확정 전(DRAFT)인 행사는 거부한다")
    void addToCart_rejectsWhenPlanSelectedButNotConfirmed() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        stubOwnerMember(ceremony);

        assertThatThrownBy(() -> ceremonyService.addToCart(
                ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(201L, 1)
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        verify(unitProductRepository, never()).findById(any());
    }

    @Test
    @DisplayName("추가구매 — 배포 전 레거시 행사(plan 없음, IN_PROGRESS)는 예전처럼 플랜 미선택으로 막지 않는다(장바구니가 비어 있으면 CART_EMPTY)")
    void purchaseUnitProducts_allowsWhenLegacyCeremonyWithoutPlan() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        ceremony.confirmPlan(); // DRAFT -> IN_PROGRESS 전이만 흉내낸다(레거시는 배포 시 이미 IN_PROGRESS로 채워졌다).
        stubOwnerMember(ceremony);
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(CEREMONY_ID)).willReturn(List.of());

        // 플랜 미선택 가드에는 안 걸린다 — 그 대신 장바구니가 비어 있어 CART_EMPTY로 끝난다
        // (자가-체크아웃 도입으로 "빈 구매"라는 개념 자체가 없어졌다).
        assertThatThrownBy(() -> ceremonyService.purchaseUnitProducts(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CART_EMPTY);
    }

    /**
     * {@code findLatestPlanHistoryForSnapshot}(DRAFT는 스냅샷을 쓰지 않는다) 회귀 방지 —
     * 2026-09-10, 실사용 중 발견: DRAFT 행사가 쓰는 플랜에 관리자가 단위 상품을 나중에 추가해도
     * (예: 태블릿을 0개 포함으로 추가) 행사 쪽에 남은 옛 스냅샷 때문에 추가구매 후보 목록에
     * 반영되지 않던 버그.
     */
    @Test
    @DisplayName("추가구매 후보 목록 — DRAFT 행사는 스냅샷이 있어도 무시하고 항상 라이브 플랜 구성을 쓴다")
    void retrievePurchasableUnitProductIds_draftIgnoresSnapshotAndUsesLive() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("플랜").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);

        UnitProduct tablets = unitProduct(901L, UnitProductType.TABLETS);
        BillingPlanUnitProduct liveLine = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(tablets).includedQuantity(0).build();
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(liveLine));

        List<Long> result = ceremonyService.retrievePurchasableUnitProductIds(ceremony);

        assertThat(result).containsExactly(901L);
        // DRAFT라 스냅샷 저장소 자체를 건드리지 않아야 한다 — 실수로 다시 조회하게 되돌아가면
        // (버그 재발) 이 검증이 실패한다.
        verify(ceremonyPlanHistoryRepository, never()).findFirstByCeremonyIdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("추가구매 후보 목록 — 확정된(IN_PROGRESS) 행사는 스냅샷이 있으면 그 스냅샷을 쓴다")
    void retrievePurchasableUnitProductIds_confirmedUsesSnapshot() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("플랜").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);
        ceremony.confirmPlan();

        CeremonyPlanHistory history = mock(CeremonyPlanHistory.class);
        given(history.getId()).willReturn(555L);
        given(ceremonyPlanHistoryRepository.findFirstByCeremonyIdOrderByCreatedAtDesc(10L)).willReturn(Optional.of(history));

        UnitProduct signers = unitProduct(902L, UnitProductType.SIGNERS);
        CeremonyPlanHistoryUnitProduct snapshotLine = mock(CeremonyPlanHistoryUnitProduct.class);
        given(snapshotLine.getUnitProduct()).willReturn(signers);
        given(ceremonyPlanHistoryUnitProductRepository.findAllByCeremonyPlanHistoryId(555L)).willReturn(List.of(snapshotLine));

        List<Long> result = ceremonyService.retrievePurchasableUnitProductIds(ceremony);

        assertThat(result).containsExactly(902L);
        // 스냅샷이 있으니 라이브 플랜 구성 조회는 아예 안 타야 한다.
        verify(billingPlanUnitProductRepository, never()).findAllByBillingPlanId(any());
    }

    @Test
    @DisplayName("추가구매 후보 목록 — 장비/인력(EQUIPMENT/PERSONNEL)은 플랜에 포함돼 있어도 걸러낸다")
    void retrievePurchasableUnitProductIds_excludesEquipmentAndPersonnel() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("플랜").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);

        UnitProduct signers = unitProduct(901L, UnitProductType.SIGNERS); // 기본 카테고리 ESSENTIAL.
        UnitProduct tablets = UnitProduct.builder()
                .type(UnitProductType.TABLETS).name("태블릿").category(UnitProductCategory.EQUIPMENT).build();
        ReflectionTestUtils.setField(tablets, "id", 902L);
        BillingPlanUnitProduct signersLine = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(signers).includedQuantity(0).build();
        BillingPlanUnitProduct tabletsLine = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(tablets).includedQuantity(0).build();
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(signersLine, tabletsLine));

        List<Long> result = ceremonyService.retrievePurchasableUnitProductIds(ceremony);

        assertThat(result).containsExactly(901L);
    }

    @Test
    @DisplayName("배타 그룹 검사 — 같은 exclusivityGroup의 단위 상품이 2개 이상이면 거부된다")
    void checkExclusivityGroups_conflictingGroup_throws() {
        UnitProduct near = UnitProduct.builder()
                .type(UnitProductType.ONSITE_SUPPORT).name("근거리 현장지원")
                .category(UnitProductCategory.PERSONNEL).exclusivityGroup("ONSITE_SUPPORT_TIER").build();
        UnitProduct far = UnitProduct.builder()
                .type(UnitProductType.ONSITE_SUPPORT).name("원거리 현장지원")
                .category(UnitProductCategory.PERSONNEL).exclusivityGroup("ONSITE_SUPPORT_TIER").build();

        assertThatThrownBy(() -> ceremonyService.checkExclusivityGroups(List.of(near, far)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_GROUP_CONFLICT);
    }

    @Test
    @DisplayName("배타 그룹 검사 — 그룹이 없거나(null) 서로 다르면 통과한다")
    void checkExclusivityGroups_noConflict_passes() {
        UnitProduct near = UnitProduct.builder()
                .type(UnitProductType.ONSITE_SUPPORT).name("근거리 현장지원")
                .category(UnitProductCategory.PERSONNEL).exclusivityGroup("ONSITE_SUPPORT_TIER").build();
        UnitProduct tablet = UnitProduct.builder()
                .type(UnitProductType.TABLETS).name("태블릿").category(UnitProductCategory.EQUIPMENT).build();
        UnitProduct onlineSupport = UnitProduct.builder()
                .type(UnitProductType.ONLINE_SUPPORT).name("온라인지원")
                .category(UnitProductCategory.PERSONNEL).exclusivityGroup("ONLINE_SUPPORT_TIER").build();

        assertThatCode(() -> ceremonyService.checkExclusivityGroups(List.of(near, tablet, onlineSupport)))
                .doesNotThrowAnyException();
    }
}
