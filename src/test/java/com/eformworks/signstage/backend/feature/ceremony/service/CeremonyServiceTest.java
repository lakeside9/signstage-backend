package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyCapacityPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOptionalFeaturePurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyCapacityPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOptionalFeaturePurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
 * 검증한다.
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
    private OrganizationRepository organizationRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BillingPlanRepository billingPlanRepository;
    @Mock
    private BillingPlanOptionalFeatureRepository billingPlanOptionalFeatureRepository;
    @Mock
    private CapacityAddOnRepository capacityAddOnRepository;
    @Mock
    private OptionalFeatureRepository optionalFeatureRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    @Mock
    private OrganizationDiscountService organizationDiscountService;

    @InjectMocks
    private CeremonyService ceremonyService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CURRENT_USER_ID = 1L;

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

    @Test
    @DisplayName("Ceremony 생성 시 조직×플랜 할인 오버라이드가 있으면 카탈로그 값 대신 그 값을 CeremonyPlanHistory에 스냅샷한다")
    void createCeremony_withPlanDiscountOverride_snapshotsOverride() {
        // given
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder()
                .name("스탠다드")
                .supplyPrice(new BigDecimal("100000"))
                .salePrice(new BigDecimal("90000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .maxSigners(10)
                .maxTemplates(10)
                .maxTestEvents(1)
                .maxMainEvents(1)
                .build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Member member = Member.builder().role(MemberRole.OWNER).build();
        User creator = User.builder().loginId("user1").name("사용자1").build();

        given(organizationRepository.findById(ORGANIZATION_ID)).willReturn(Optional.of(organization));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(billingPlanRepository.findById(101L)).willReturn(Optional.of(plan));
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(creator));

        OrganizationDiscountService.EffectiveDiscount overrideDiscount =
                new OrganizationDiscountService.EffectiveDiscount(DiscountType.PERCENT, new BigDecimal("30"));
        given(organizationDiscountService.resolveBillingPlanDiscount(organization, plan)).willReturn(overrideDiscount);

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

        CapacityAddOn addOn = CapacityAddOn.builder()
                .capacityType(CapacityType.SIGNERS)
                .unitAmount(10)
                .supplyPrice(new BigDecimal("50000"))
                .salePrice(new BigDecimal("45000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .build();
        ReflectionTestUtils.setField(addOn, "id", 201L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(capacityAddOnRepository.findById(201L)).willReturn(Optional.of(addOn));

        OrganizationDiscountService.EffectiveDiscount overrideDiscount =
                new OrganizationDiscountService.EffectiveDiscount(DiscountType.PERCENT, new BigDecimal("20"));
        given(organizationDiscountService.resolveCapacityAddOnDiscount(organization, addOn)).willReturn(overrideDiscount);

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
                .supplyPrice(new BigDecimal("100000"))
                .salePrice(new BigDecimal("90000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.ZERO)
                .build();
        ReflectionTestUtils.setField(comboAddOn, "id", 301L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(capacityAddOnRepository.findById(301L)).willReturn(Optional.of(comboAddOn));
        given(organizationDiscountService.resolveCapacityAddOnDiscount(organization, comboAddOn))
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
        BillingPlan plan = BillingPlan.builder()
                .name("스탠다드")
                .supplyPrice(new BigDecimal("100000"))
                .salePrice(new BigDecimal("90000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.ZERO)
                .maxSigners(10)
                .maxTemplates(10)
                .maxTestEvents(1)
                .maxMainEvents(1)
                .build();
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);

        CapacityAddOn comboAddOn = CapacityAddOn.builder()
                .capacityType(CapacityType.SIGNERS)
                .unitAmount(10)
                .secondaryCapacityType(CapacityType.TABLETS)
                .secondaryUnitAmount(10)
                .supplyPrice(new BigDecimal("100000"))
                .salePrice(new BigDecimal("90000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.ZERO)
                .build();
        ReflectionTestUtils.setField(comboAddOn, "id", 301L);

        CeremonyCapacityPurchase comboPurchase = CeremonyCapacityPurchase.builder()
                .ceremony(ceremony)
                .capacityAddOn(comboAddOn)
                .quantity(2)
                .purchasedUnitAmount(10)
                .purchasedSecondaryUnitAmount(10)
                .purchasedSalePrice(new BigDecimal("90000"))
                .purchasedDiscountType(DiscountType.FIXED_AMOUNT)
                .purchasedDiscountValue(BigDecimal.ZERO)
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
                .supplyPrice(new BigDecimal("30000"))
                .salePrice(new BigDecimal("27000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("3000"))
                .build();
        ReflectionTestUtils.setField(feature, "id", 301L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(optionalFeatureRepository.findById(301L)).willReturn(Optional.of(feature));

        // 오버라이드 없음 — OrganizationDiscountService가 카탈로그 값을 그대로 돌려주는 상황을 흉내낸다.
        given(organizationDiscountService.resolveOptionalFeatureDiscount(organization, feature))
                .willReturn(new OrganizationDiscountService.EffectiveDiscount(feature.getDiscountType(), feature.getDiscountValue()));

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
        given(ceremonyRepository.search(ORGANIZATION_ID, null, null, null, pageable)).willReturn(page);

        // when
        Page<CeremonyDto.Response.CeremonySummary> result =
                ceremonyService.findCeremoniesByPlatformAdmin(ORGANIZATION_ID, null, null, pageable);

        // then — memberRepository는 이 흐름에서 전혀 조회되지 않는다(플랫폼 관리자는 조직 멤버가 아니어도 된다).
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        verify(memberRepository, never()).findByOrganizationIdAndUserIdAndStatus(any(), any(), any());
    }
}
