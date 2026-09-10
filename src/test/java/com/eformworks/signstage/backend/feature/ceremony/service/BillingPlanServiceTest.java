package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingPlanDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanType;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanDiscountPeriodHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationSubscriptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link BillingPlanService#deletePlan}의 "사용 이력이 전혀 없어야 삭제 가능" 규칙 단위 테스트 —
 * signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 11장 결정
 * (2026-09-10, 단위 상품 삭제와 같은 조건). 사용 이력 5곳 중 어느 하나라도 걸리면 거부되는지,
 * 전부 없으면 자신의 구성/할인 기간/편집 이력을 함께 지우고 삭제가 성공하는지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class BillingPlanServiceTest {

    @Mock
    private BillingPlanRepository billingPlanRepository;
    @Mock
    private BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    @Mock
    private BillingPlanHistoryRepository billingPlanHistoryRepository;
    @Mock
    private BillingPlanHistoryUnitProductRepository billingPlanHistoryUnitProductRepository;
    @Mock
    private BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;
    @Mock
    private BillingPlanDiscountPeriodHistoryRepository billingPlanDiscountPeriodHistoryRepository;
    @Mock
    private UnitProductRepository unitProductRepository;
    @Mock
    private UnitProductPricePeriodRepository unitProductPricePeriodRepository;
    @Mock
    private CeremonyRepository ceremonyRepository;
    @Mock
    private CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    @Mock
    private OrganizationSubscriptionRepository organizationSubscriptionRepository;
    @Mock
    private OrganizationBillingPlanDiscountRepository organizationBillingPlanDiscountRepository;
    @Mock
    private OrganizationBillingPlanDiscountHistoryRepository organizationBillingPlanDiscountHistoryRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private BillingPlanService billingPlanService;

    private static final Long PLAN_ID = 701L;

    private BillingPlan plan() {
        BillingPlan plan = BillingPlan.builder()
                .name("Standard")
                .planType(BillingPlanType.STANDARD)
                .build();
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        return plan;
    }

    @BeforeEach
    void stubManagePermission() {
        // 삭제 관련 시나리오만 이 스텁을 쓴다(목록 조회 테스트는 checkAllowed를 타지 않음) —
        // UnitProductServiceTest와 같은 이유로 lenient 처리한다.
        lenient().when(rolePermissionService.isAllowed(eq("PLATFORM_OPS"), anyString())).thenReturn(true);
    }

    private void stubAllUsageChecksFalse() {
        given(ceremonyRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(ceremonyPlanHistoryRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(organizationSubscriptionRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(organizationBillingPlanDiscountRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(organizationBillingPlanDiscountHistoryRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
    }

    @Test
    @DisplayName("삭제 — 5곳 어디에도 사용 이력이 없으면 구성/할인 기간/편집 이력을 함께 지우고 삭제한다")
    void deletePlan_deletesWhenNeverUsed() {
        BillingPlan plan = plan();
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan));
        stubAllUsageChecksFalse();

        billingPlanService.deletePlan(PLAN_ID, "PLATFORM_OPS", 1L);

        verify(billingPlanHistoryUnitProductRepository).deleteAllByBillingPlanHistory_BillingPlanId(PLAN_ID);
        verify(billingPlanHistoryRepository).deleteAllByBillingPlanId(PLAN_ID);
        verify(billingPlanDiscountPeriodHistoryRepository).deleteAllByBillingPlanId(PLAN_ID);
        verify(billingPlanDiscountPeriodRepository).deleteAllByBillingPlanId(PLAN_ID);
        verify(billingPlanUnitProductRepository).deleteAllByBillingPlanId(PLAN_ID);
        verify(billingPlanRepository).delete(plan);
    }

    @Test
    @DisplayName("삭제 — 지금 이 플랜을 쓰는 행사가 있으면 거부하고 아무것도 지우지 않는다")
    void deletePlan_rejectsWhenUsedByLiveCeremony() {
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan()));
        given(ceremonyRepository.existsByBillingPlanId(PLAN_ID)).willReturn(true);

        assertThatThrownBy(() -> billingPlanService.deletePlan(PLAN_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.BILLING_PLAN_IN_USE);

        verify(billingPlanRepository, never()).delete(any(BillingPlan.class));
    }

    @Test
    @DisplayName("삭제 — 과거 행사에 스냅샷된 적이 있으면(현재 플랜 구성과 무관) 거부한다")
    void deletePlan_rejectsWhenEverSnapshottedIntoCeremony() {
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan()));
        given(ceremonyRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(ceremonyPlanHistoryRepository.existsByBillingPlanId(PLAN_ID)).willReturn(true);

        assertThatThrownBy(() -> billingPlanService.deletePlan(PLAN_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.BILLING_PLAN_IN_USE);

        verify(billingPlanRepository, never()).delete(any(BillingPlan.class));
    }

    @Test
    @DisplayName("삭제 — 조직이 구독한 적이 있으면(하드 삭제되지 않으므로 현재/과거 무관) 거부한다")
    void deletePlan_rejectsWhenSubscribed() {
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan()));
        given(ceremonyRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(ceremonyPlanHistoryRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(organizationSubscriptionRepository.existsByBillingPlanId(PLAN_ID)).willReturn(true);

        assertThatThrownBy(() -> billingPlanService.deletePlan(PLAN_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.BILLING_PLAN_IN_USE);

        verify(billingPlanRepository, never()).delete(any(BillingPlan.class));
    }

    @Test
    @DisplayName("삭제 — 조직×플랜 할인 오버라이드가 제거됐더라도 이력이 남아있으면 거부한다")
    void deletePlan_rejectsWhenOrgDiscountOverrideEverExisted() {
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan()));
        given(ceremonyRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(ceremonyPlanHistoryRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(organizationSubscriptionRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(organizationBillingPlanDiscountRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(organizationBillingPlanDiscountHistoryRepository.existsByBillingPlanId(PLAN_ID)).willReturn(true);

        assertThatThrownBy(() -> billingPlanService.deletePlan(PLAN_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.BILLING_PLAN_IN_USE);

        verify(billingPlanRepository, never()).delete(any(BillingPlan.class));
    }

    @Test
    @DisplayName("삭제 — 존재하지 않는 플랜이면 BILLING_PLAN_NOT_FOUND")
    void deletePlan_throwsWhenNotFound() {
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> billingPlanService.deletePlan(PLAN_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.BILLING_PLAN_NOT_FOUND);
    }

    @Test
    @DisplayName("목록 조회 — canDelete는 사용 이력이 전혀 없을 때만 true다")
    void findPlans_computesCanDeleteFromUsage() {
        BillingPlan plan = plan();
        given(billingPlanRepository.findAll()).willReturn(java.util.List.of(plan));
        given(billingPlanDiscountPeriodRepository.findEffective(any(), any())).willReturn(Optional.empty());
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(PLAN_ID)).willReturn(java.util.List.of());
        given(ceremonyRepository.countByBillingPlanId(PLAN_ID)).willReturn(0L);
        stubAllUsageChecksFalse();

        var result = billingPlanService.findPlans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isCanDelete()).isTrue();
    }

    /**
     * 회귀 방지(2026-09-10) — {@code deleteAllByBillingPlanId}는 파생 delete 쿼리라 DELETE SQL을
     * 즉시 내보내지 않는다. 뒤이은 {@code saveUnitProducts}의 save()는 IDENTITY 채번이라 즉시
     * INSERT를 실행하는데, 그 사이 flush가 없으면 그대로 남아있는 단위 상품(수정 후에도 구성에
     * 남는 상품)의 INSERT가 아직 DB에 남은 옛 행과 충돌해
     * {@code billing_plan_unit_products.uq_bpup_plan_product} 유니크 제약 위반
     * (Duplicate entry '{planId}-{unitProductId}')으로 실패했다 — 실제 운영 사례로 발견했다.
     * Mockito로는 실제 flush 타이밍을 재현할 수 없으므로, 고친 순서(delete → flush → save)를
     * 그대로 호출하는지만 검증한다.
     */
    @Test
    @DisplayName("수정 — 단위 상품 구성을 통째로 교체할 때 삭제를 먼저 flush한 뒤 다시 저장한다")
    void updatePlan_flushesDeleteBeforeReinsertingUnitProducts() {
        BillingPlan plan = plan();
        given(billingPlanRepository.findById(PLAN_ID)).willReturn(Optional.of(plan));

        UnitProduct unitProduct = UnitProduct.builder()
                .type(UnitProductType.SIGNERS)
                .name("서명자")
                .category(UnitProductCategory.EQUIPMENT)
                .build();
        ReflectionTestUtils.setField(unitProduct, "id", 55L);
        given(unitProductRepository.findAllByIdIn(java.util.List.of(55L))).willReturn(java.util.List.of(unitProduct));
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(PLAN_ID)).willReturn(java.util.List.of());

        BillingPlanDto.Request.UpdatePlan request = new BillingPlanDto.Request.UpdatePlan(
                "Standard", java.util.List.of(new BillingPlanDto.Request.PlanUnitProductLine(55L, 5))
        );

        billingPlanService.updatePlan(PLAN_ID, "PLATFORM_OPS", 1L, request);

        InOrder order = inOrder(billingPlanUnitProductRepository);
        order.verify(billingPlanUnitProductRepository).deleteAllByBillingPlanId(PLAN_ID);
        order.verify(billingPlanUnitProductRepository).flush();
        order.verify(billingPlanUnitProductRepository).save(any());
    }
}
