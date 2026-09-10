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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodHistoryRepository;
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
 * {@link UnitProductService#deleteUnitProduct}의 "사용 이력이 전혀 없어야 삭제 가능" 규칙
 * 단위 테스트 — signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정(2026-09-10 삭제 기능
 * 추가). 사용 이력 6곳 중 어느 하나라도 걸리면 거부되는지, 전부 없으면 자신의 가격 기간/이력을
 * 함께 지우고 삭제가 성공하는지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class UnitProductServiceTest {

    @Mock
    private UnitProductRepository unitProductRepository;
    @Mock
    private UnitProductHistoryRepository unitProductHistoryRepository;
    @Mock
    private UnitProductPricePeriodRepository unitProductPricePeriodRepository;
    @Mock
    private UnitProductPricePeriodHistoryRepository unitProductPricePeriodHistoryRepository;
    @Mock
    private BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    @Mock
    private BillingPlanHistoryUnitProductRepository billingPlanHistoryUnitProductRepository;
    @Mock
    private CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    @Mock
    private CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    @Mock
    private CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    @Mock
    private CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    @Mock
    private CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private UnitProductService unitProductService;

    private static final Long UNIT_PRODUCT_ID = 901L;

    private UnitProduct unitProduct() {
        UnitProduct unitProduct = UnitProduct.builder()
                .type(UnitProductType.SIGNERS)
                .name("서명자")
                .category(UnitProductCategory.EQUIPMENT)
                .build();
        ReflectionTestUtils.setField(unitProduct, "id", UNIT_PRODUCT_ID);
        return unitProduct;
    }

    @BeforeEach
    void stubManagePermission() {
        // 삭제 관련 시나리오만 이 스텁을 쓴다(목록 조회 테스트는 checkAllowed를 타지 않음) —
        // CeremonyServiceTest와 같은 이유로 lenient 처리한다.
        lenient().when(rolePermissionService.isAllowed(eq("PLATFORM_OPS"), anyString())).thenReturn(true);
    }

    private CeremonyEffectDefinition effectDefinition(Long id) {
        CeremonyEffectDefinition definition = CeremonyEffectDefinition.builder()
                .code("EFFECT_" + id)
                .targetType(CeremonyEffectTarget.PROJECTOR)
                .triggerType(CeremonyEffectTrigger.SIGNATURE_COMPLETED)
                .displayName("효과 " + id)
                .rendererKey("renderer")
                .displayOrder(0)
                .build();
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }

    private void stubAllUsageChecksFalse() {
        given(billingPlanUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(billingPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyUnitProductPurchaseLineRepository.existsByUnitProduct_Id(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyEventOptionalFeatureRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyEffectDefinitionOptionRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
    }

    @Test
    @DisplayName("삭제 — 6곳 어디에도 사용 이력이 없으면 가격 기간/이력을 함께 지우고 삭제한다")
    void deleteUnitProduct_deletesWhenNeverUsed() {
        UnitProduct unitProduct = unitProduct();
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(unitProduct));
        stubAllUsageChecksFalse();

        unitProductService.deleteUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L);

        verify(unitProductPricePeriodHistoryRepository).deleteAllByUnitProductId(UNIT_PRODUCT_ID);
        verify(unitProductPricePeriodRepository).deleteAllByUnitProductId(UNIT_PRODUCT_ID);
        verify(unitProductHistoryRepository).deleteAllByUnitProductId(UNIT_PRODUCT_ID);
        verify(unitProductRepository).delete(unitProduct);
    }

    @Test
    @DisplayName("삭제 — 현재 플랜 구성에 포함돼 있으면 거부하고 아무것도 지우지 않는다")
    void deleteUnitProduct_rejectsWhenIncludedInLivePlan() {
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(unitProduct()));
        given(billingPlanUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(true);

        assertThatThrownBy(() -> unitProductService.deleteUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_IN_USE);

        verify(unitProductRepository, never()).delete(any(UnitProduct.class));
    }

    @Test
    @DisplayName("삭제 — 과거 행사에 스냅샷된 적이 있으면(현재 플랜 구성과 무관) 거부한다")
    void deleteUnitProduct_rejectsWhenEverSnapshottedIntoCeremony() {
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(unitProduct()));
        given(billingPlanUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(billingPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(true);

        assertThatThrownBy(() -> unitProductService.deleteUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_IN_USE);

        verify(unitProductRepository, never()).delete(any(UnitProduct.class));
    }

    @Test
    @DisplayName("삭제 — 존재하지 않는 단위 상품이면 UNIT_PRODUCT_NOT_FOUND")
    void deleteUnitProduct_throwsWhenNotFound() {
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> unitProductService.deleteUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("목록 조회 — canDelete는 사용 이력이 전혀 없을 때만 true다")
    void findUnitProducts_computesCanDeleteFromUsage() {
        UnitProduct unitProduct = unitProduct();
        given(unitProductRepository.findAll()).willReturn(java.util.List.of(unitProduct));
        given(unitProductPricePeriodRepository.findEffective(any(), any())).willReturn(Optional.empty());
        given(ceremonyEffectDefinitionOptionRepository.findAllByUnitProductId(UNIT_PRODUCT_ID)).willReturn(java.util.List.of());
        given(ceremonyUnitProductPurchaseLineRepository.countByUnitProduct_IdAndPurchase_Status(any(), any())).willReturn(0L);
        stubAllUsageChecksFalse();

        var result = unitProductService.findUnitProducts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isCanDelete()).isTrue();
    }

    /**
     * 회귀 방지(2026-09-10) — 사용자가 이벤트 효과 묶음 단위 상품을 등록하면서
     * {@code effectDefinitionIds}를 지정했는데 "EVENT_EFFECT_BUNDLE 종류에서만 지정할 수
     * 있다"는 오류가 그 종류를 등록하는데도 나서 발견됐다. 원인은 이 검증
     * ({@code checkEffectDefinitionIdsAllowed})이 실제로는 항상 거부하는 1단계 임시
     * 구현이었던 것 — 이번에 진짜로 {@code CeremonyEffectDefinitionOption} 행을 만들도록 고쳤다.
     */
    @Test
    @DisplayName("생성 — EVENT_EFFECT_BUNDLE에 effectDefinitionIds를 지정하면 매핑을 만든다")
    void createUnitProduct_savesEffectDefinitionOptionsForEventEffectBundle() {
        CeremonyEffectDefinition definition1 = effectDefinition(10L);
        CeremonyEffectDefinition definition2 = effectDefinition(20L);
        given(ceremonyEffectDefinitionRepository.findAllById(java.util.List.of(10L, 20L)))
                .willReturn(java.util.List.of(definition1, definition2));

        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "EVENT_EFFECT_BUNDLE", "3종 묶음", "APPLICATION", null, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true,
                null, null, java.util.List.of(10L, 20L)
        );

        unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request);

        verify(ceremonyEffectDefinitionOptionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("생성 — EVENT_EFFECT_BUNDLE이 아닌데 effectDefinitionIds가 있으면 거부한다")
    void createUnitProduct_rejectsEffectDefinitionIdsForNonBundleType() {
        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "SIGNERS", "서명자", "ESSENTIAL", null, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true,
                null, null, java.util.List.of(10L)
        );

        assertThatThrownBy(() -> unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_EFFECT_BUNDLE_ONLY);

        verify(ceremonyEffectDefinitionOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성 — 존재하지 않는 effectDefinitionId가 섞여 있으면 EFFECT_DEFINITION_NOT_FOUND")
    void createUnitProduct_throwsWhenEffectDefinitionNotFound() {
        given(ceremonyEffectDefinitionRepository.findAllById(java.util.List.of(10L)))
                .willReturn(java.util.List.of());

        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "EVENT_EFFECT_BUNDLE", "3종 묶음", "APPLICATION", null, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true,
                null, null, java.util.List.of(10L)
        );

        assertThatThrownBy(() -> unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND);
    }

    @Test
    @DisplayName("수정 — effectDefinitionIds를 생략하면(null) 기존 구성을 건드리지 않는다")
    void updateUnitProduct_keepsExistingWhenEffectDefinitionIdsOmitted() {
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", UNIT_PRODUCT_ID);
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(bundle));

        UnitProductDto.Request.UpdateUnitProduct request =
                new UnitProductDto.Request.UpdateUnitProduct("3종 묶음", "APPLICATION", null, null);

        unitProductService.updateUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L, request);

        verify(ceremonyEffectDefinitionOptionRepository, never()).deleteAllByUnitProductId(any());
        verify(ceremonyEffectDefinitionOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("수정 — effectDefinitionIds를 명시하면 delete → flush → save 순서로 통째로 교체한다")
    void updateUnitProduct_replacesEffectDefinitionOptionsInOrder() {
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", UNIT_PRODUCT_ID);
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(bundle));
        given(ceremonyEffectDefinitionRepository.findAllById(java.util.List.of(10L)))
                .willReturn(java.util.List.of(effectDefinition(10L)));

        UnitProductDto.Request.UpdateUnitProduct request =
                new UnitProductDto.Request.UpdateUnitProduct("3종 묶음", "APPLICATION", null, java.util.List.of(10L));

        unitProductService.updateUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L, request);

        InOrder order = inOrder(ceremonyEffectDefinitionOptionRepository);
        order.verify(ceremonyEffectDefinitionOptionRepository).deleteAllByUnitProductId(UNIT_PRODUCT_ID);
        order.verify(ceremonyEffectDefinitionOptionRepository).flush();
        order.verify(ceremonyEffectDefinitionOptionRepository).save(any());
    }

    @Test
    @DisplayName("수정 — 빈 배열을 명시하면 전부 해제하고 새로 만들지 않는다")
    void updateUnitProduct_clearsEffectDefinitionOptionsOnEmptyList() {
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", UNIT_PRODUCT_ID);
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(bundle));

        UnitProductDto.Request.UpdateUnitProduct request =
                new UnitProductDto.Request.UpdateUnitProduct("3종 묶음", "APPLICATION", null, java.util.List.of());

        unitProductService.updateUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L, request);

        verify(ceremonyEffectDefinitionOptionRepository).deleteAllByUnitProductId(UNIT_PRODUCT_ID);
        verify(ceremonyEffectDefinitionOptionRepository, never()).save(any());
    }
}
