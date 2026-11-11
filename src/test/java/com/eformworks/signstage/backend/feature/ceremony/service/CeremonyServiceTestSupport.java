package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.eformworks.signstage.backend.feature.billing.service.OrganizationDiscountService;
import com.eformworks.signstage.backend.feature.billing.service.BillingCatalogQueryService;
import com.eformworks.signstage.backend.feature.billing.service.OrganizationSubscriptionService;
import com.eformworks.signstage.backend.feature.billing.service.TaxPolicyResolver;
import com.eformworks.signstage.backend.core.money.MoneyCalculator;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductCartLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventEffectSettingRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyInquiryMessageRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyInquiryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOnsiteSupportRequestRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductCartLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ceremony 서비스 단위 테스트 공용 기반 — Repository와 billing 협력 객체는 mock으로 두고, 2026-11-11
 * {@code CeremonyService}에서 나눈 서비스들은 실제 객체로 조립한다({@link #setUpServices}). 분할 전
 * 한 클래스 안에서 함께 검증하던 흐름(접근 검사 → 카탈로그 판정 → 플랜 스냅샷/구매 저장)을 그대로
 * 유지하기 위해서다. {@link OrganizationDiscountService}는 mock이고, 오버라이드 해석 로직은
 * {@code OrganizationDiscountServiceTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
abstract class CeremonyServiceTestSupport {

    @Mock
    protected CeremonyRepository ceremonyRepository;

    @Mock
    protected CeremonyAssignmentRepository ceremonyAssignmentRepository;

    @Mock
    protected CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;

    @Mock
    protected CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;

    @Mock
    protected CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;

    @Mock
    protected CeremonyEventRepository ceremonyEventRepository;

    @Mock
    protected CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;

    @Mock
    protected CeremonyEventEffectSettingRepository ceremonyEventEffectSettingRepository;

    @Mock
    protected CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;

    @Mock
    protected CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;

    @Mock
    protected CeremonyUnitProductCartLineRepository ceremonyUnitProductCartLineRepository;

    @Mock
    protected CeremonyInquiryRepository ceremonyInquiryRepository;

    @Mock
    protected CeremonyInquiryMessageRepository ceremonyInquiryMessageRepository;

    @Mock
    protected CeremonyOnsiteSupportRequestRepository ceremonyOnsiteSupportRequestRepository;

    @Mock
    protected BillingPlanUnitProductRepository billingPlanUnitProductRepository;

    @Mock
    protected OrganizationRepository organizationRepository;

    @Mock
    protected MemberRepository memberRepository;

    @Mock
    protected BillingPlanRepository billingPlanRepository;

    @Mock
    protected BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;

    @Mock
    protected UnitProductRepository unitProductRepository;

    @Mock
    protected UnitProductPricePeriodRepository unitProductPricePeriodRepository;

    @Mock
    protected UserRepository userRepository;

    @Mock
    protected PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    @Mock
    protected OrganizationDiscountService organizationDiscountService;

    @Spy
    protected MoneyCalculator moneyCalculator = new MoneyCalculator();

    @Mock
    protected TaxPolicyResolver taxPolicyResolver;

    @Mock
    protected RolePermissionService rolePermissionService;

    @Mock
    protected OrganizationSubscriptionService organizationSubscriptionService;

    protected static final Long ORGANIZATION_ID = 1L;

    protected static final Long CURRENT_USER_ID = 1L;

    protected static final Long CEREMONY_ID = 10L;

    protected BillingCatalogQueryService billingCatalogQueryService;
    protected CeremonyAccessService ceremonyAccessService;
    protected CeremonyCatalogService ceremonyCatalogService;
    protected CeremonyPlanService ceremonyPlanService;
    protected CeremonyQuoteService ceremonyQuoteService;
    protected CeremonyUnitProductPurchaseService ceremonyUnitProductPurchaseService;
    protected CeremonyUnitProductPurchaseApprovalService ceremonyUnitProductPurchaseApprovalService;
    protected CeremonyService ceremonyService;
    protected CeremonyDeletionService ceremonyDeletionService;

    @BeforeEach
    void setUpServices() {
        billingCatalogQueryService = new BillingCatalogQueryService(
                billingPlanRepository,
                billingPlanUnitProductRepository,
                billingPlanDiscountPeriodRepository,
                unitProductRepository,
                unitProductPricePeriodRepository
        );
        ceremonyAccessService = new CeremonyAccessService(
                ceremonyRepository,
                ceremonyAssignmentRepository,
                organizationRepository,
                memberRepository,
                userRepository,
                rolePermissionService
        );
        ceremonyCatalogService = new CeremonyCatalogService(
                ceremonyUnitProductPurchaseLineRepository,
                ceremonyEffectDefinitionOptionRepository,
                ceremonyPlanHistoryRepository,
                ceremonyPlanHistoryUnitProductRepository,
                billingCatalogQueryService,
                ceremonyAccessService
        );
        ceremonyPlanService = new CeremonyPlanService(
                ceremonyPlanHistoryRepository,
                ceremonyPlanHistoryUnitProductRepository,
                billingCatalogQueryService,
                organizationSubscriptionService,
                organizationDiscountService,
                ceremonyAccessService,
                ceremonyCatalogService
        );
        ceremonyQuoteService = new CeremonyQuoteService(
                ceremonyUnitProductPurchaseLineRepository,
                ceremonyPlanHistoryUnitProductRepository,
                billingCatalogQueryService,
                moneyCalculator,
                taxPolicyResolver,
                ceremonyAccessService,
                ceremonyCatalogService
        );
        ceremonyUnitProductPurchaseService = new CeremonyUnitProductPurchaseService(
                ceremonyUnitProductPurchaseRepository,
                ceremonyUnitProductPurchaseLineRepository,
                ceremonyUnitProductCartLineRepository,
                billingCatalogQueryService,
                ceremonyAccessService,
                ceremonyCatalogService
        );
        ceremonyUnitProductPurchaseApprovalService = new CeremonyUnitProductPurchaseApprovalService(
                ceremonyUnitProductPurchaseRepository,
                ceremonyUnitProductPurchaseLineRepository,
                ceremonyEffectDefinitionOptionRepository,
                ceremonyEventRepository,
                ceremonyEventOptionalFeatureRepository,
                ceremonyEventEffectSettingRepository,
                userRepository,
                platformAdminAuditLogRecorder,
                rolePermissionService
        );
        ceremonyService = new CeremonyService(
                ceremonyRepository,
                ceremonyAssignmentRepository,
                organizationRepository,
                userRepository,
                billingCatalogQueryService,
                platformAdminAuditLogRecorder,
                rolePermissionService,
                ceremonyAccessService,
                ceremonyCatalogService,
                ceremonyPlanService
        );
        ceremonyDeletionService = new CeremonyDeletionService(
                ceremonyRepository,
                ceremonyAssignmentRepository,
                ceremonyUnitProductPurchaseRepository,
                ceremonyUnitProductPurchaseLineRepository,
                ceremonyPlanHistoryRepository,
                ceremonyPlanHistoryUnitProductRepository,
                ceremonyUnitProductCartLineRepository,
                ceremonyInquiryRepository,
                ceremonyInquiryMessageRepository,
                ceremonyOnsiteSupportRequestRepository,
                ceremonyAccessService
        );
    }

    @BeforeEach
    void setUpPermissions() {
        // 이 테스트 파일의 시나리오는 전부 OWNER라 생성/관리 액션이 항상 허용된다고 가정한다 —
        // 권한 자체의 허용/거부 판단은 RolePermissionServiceTest가 검증한다.
        lenient().when(rolePermissionService.isAllowed(eq("OWNER"), anyString())).thenReturn(true);
        // 취소(플랫폼 관리자 축) 테스트가 쓴다 — 권한 자체의 허용/거부 판단은
        // RolePermissionServiceTest가 검증한다.
        lenient().when(rolePermissionService.isAllowed(eq("PLATFORM_OPS"), anyString())).thenReturn(true);
    }

    protected Organization organization() {
        Organization organization = Organization.builder().name("조직").code("ORG1").build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);
        return organization;
    }

    protected Ceremony ceremony(Organization organization, Long id) {
        Ceremony ceremony = Ceremony.builder().organization(organization).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", id);
        return ceremony;
    }

    protected UnitProduct unitProduct(Long id, UnitProductType type) {
        UnitProduct unitProduct = UnitProduct.builder()
                .type(type).name(type.name()).category(UnitProductCategory.ESSENTIAL).build();
        ReflectionTestUtils.setField(unitProduct, "id", id);
        return unitProduct;
    }

    protected CeremonyUnitProductCartLine cartLine(Ceremony ceremony, UnitProduct unitProduct, int quantity) {
        return CeremonyUnitProductCartLine.builder().ceremony(ceremony).unitProduct(unitProduct).quantity(quantity).build();
    }

    protected UnitProductPricePeriod unitProductPeriod(UnitProduct unitProduct, BigDecimal supplyPrice, BigDecimal salePrice) {
        return UnitProductPricePeriod.builder()
                .unitProduct(unitProduct)
                .supplyPrice(supplyPrice).salePrice(salePrice)
                .active(true).effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    protected BillingPlanDiscountPeriod planDiscountPeriod(BillingPlan plan, DiscountType discountType, BigDecimal discountValue) {
        return BillingPlanDiscountPeriod.builder()
                .billingPlan(plan)
                .discountType(discountType).discountValue(discountValue)
                .active(true).effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    protected CeremonyUnitProductPurchaseLine purchaseLine(UnitProduct unitProduct, int quantity) {
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

    protected void stubOwnerMember(Ceremony ceremony) {
        Member member = Member.builder().role(MemberRole.OWNER).build();
        given(ceremonyRepository.findById(CEREMONY_ID)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
    }
}
