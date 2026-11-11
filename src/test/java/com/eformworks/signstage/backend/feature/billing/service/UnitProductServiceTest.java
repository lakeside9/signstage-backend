package com.eformworks.signstage.backend.feature.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.web.DisplayOrderRequest;
import com.eformworks.signstage.backend.feature.billing.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.error.BillingErrorCode;
import com.eformworks.signstage.backend.feature.billing.port.CeremonyBillingUsagePort;
import com.eformworks.signstage.backend.feature.billing.port.UnitProductEffectPort;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductHistoryRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductPricePeriodHistoryRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link UnitProductService#deleteUnitProduct}의 "사용 이력이 전혀 없어야 삭제 가능" 규칙
 * 단위 테스트 — signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정(2026-09-10 삭제 기능
 * 추가). 사용 이력(카탈로그 2곳 + 행사 쪽 {@link CeremonyBillingUsagePort}) 중 어느 하나라도
 * 걸리면 거부되는지, 전부 없으면 자신의 가격 기간/이력을 함께 지우고 삭제가 성공하는지를 확인한다.
 * 효과 정의 매핑 자체의 저장 순서·검증은 {@code UnitProductEffectServiceTest}가 다룬다.
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
    private CeremonyBillingUsagePort ceremonyBillingUsagePort;
    @Mock
    private UnitProductEffectPort unitProductEffectPort;
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

    private void stubAllUsageChecksFalse() {
        given(billingPlanUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(billingPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyBillingUsagePort.isUnitProductUsed(UNIT_PRODUCT_ID)).willReturn(false);
    }

    @Test
    @DisplayName("삭제 — 어디에도 사용 이력이 없으면 가격 기간/이력을 함께 지우고 삭제한다")
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
                .isEqualTo(BillingErrorCode.UNIT_PRODUCT_IN_USE);

        verify(unitProductRepository, never()).delete(any(UnitProduct.class));
    }

    @Test
    @DisplayName("삭제 — 행사 쪽(스냅샷·구매·옵션 기능·효과 묶음)에서 쓴 적이 있으면(현재 플랜 구성과 무관) 거부한다")
    void deleteUnitProduct_rejectsWhenUsedByCeremony() {
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(unitProduct()));
        given(billingPlanUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(billingPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyBillingUsagePort.isUnitProductUsed(UNIT_PRODUCT_ID)).willReturn(true);

        assertThatThrownBy(() -> unitProductService.deleteUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(BillingErrorCode.UNIT_PRODUCT_IN_USE);

        verify(unitProductRepository, never()).delete(any(UnitProduct.class));
    }

    @Test
    @DisplayName("삭제 — 존재하지 않는 단위 상품이면 UNIT_PRODUCT_NOT_FOUND")
    void deleteUnitProduct_throwsWhenNotFound() {
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> unitProductService.deleteUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(BillingErrorCode.UNIT_PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("목록 조회 — canDelete는 사용 이력이 전혀 없을 때만 true다")
    void findUnitProducts_computesCanDeleteFromUsage() {
        UnitProduct unitProduct = unitProduct();
        given(unitProductRepository.findAllByOrderByDisplayOrderAscIdAsc()).willReturn(java.util.List.of(unitProduct));
        given(unitProductPricePeriodRepository.findEffective(any(), any())).willReturn(Optional.empty());
        given(unitProductEffectPort.findEffectDefinitionIds(UNIT_PRODUCT_ID)).willReturn(java.util.List.of());
        given(ceremonyBillingUsagePort.countApprovedPurchases(UNIT_PRODUCT_ID)).willReturn(0L);
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
    void createUnitProduct_linksEffectDefinitionsForEventEffectBundle() {
        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "EVENT_EFFECT_BUNDLE", "3종 묶음", null, "APPLICATION", null, null, null, null, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true,
                null, null, java.util.List.of(10L, 20L)
        );

        unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request);

        verify(unitProductEffectPort).linkEffectDefinitions(any(UnitProduct.class), eq(java.util.List.of(10L, 20L)));
    }

    @Test
    @DisplayName("생성 — EVENT_EFFECT_BUNDLE이 아닌데 effectDefinitionIds가 있으면 거부한다")
    void createUnitProduct_rejectsEffectDefinitionIdsForNonBundleType() {
        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "SIGNERS", "서명자", null, "ESSENTIAL", null, null, null, null, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true,
                null, null, java.util.List.of(10L)
        );

        assertThatThrownBy(() -> unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(BillingErrorCode.UNIT_PRODUCT_EFFECT_BUNDLE_ONLY);

        verify(unitProductEffectPort, never()).linkEffectDefinitions(any(), any());
    }

    /**
     * 2026-09-14 사용자 요청("템플릿 문서 10개를 1묶음 10,000원에 팔고 싶다") 구현 — 판매가는
     * 개당 단가로 유지하고 saleUnitQuantity로 구매 단위(배수)만 강제한다.
     */
    @Test
    @DisplayName("생성 — saleUnitQuantity를 함께 저장한다")
    void createUnitProduct_savesSaleUnitQuantity() {
        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "TEMPLATES", "템플릿 문서", null, "ESSENTIAL", null, null, 10, null, "KRW",
                java.math.BigDecimal.valueOf(500), java.math.BigDecimal.valueOf(1000), "KR_VAT_STANDARD", true,
                null, null, null
        );

        UnitProductDto.Response.UnitProductSummary result = unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request);

        assertThat(result.getSaleUnitQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("생성 — 최대 구매 수량이 판매 단위 수량의 배수가 아니면 거부한다")
    void createUnitProduct_rejectsMaxPurchaseQuantityNotMultipleOfSaleUnit() {
        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "TEMPLATES", "템플릿 문서", null, "ESSENTIAL", null, 25, 10, null, "KRW",
                java.math.BigDecimal.valueOf(500), java.math.BigDecimal.valueOf(1000), "KR_VAT_STANDARD", true,
                null, null, null
        );

        assertThatThrownBy(() -> unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(BillingErrorCode.UNIT_PRODUCT_MAX_QUANTITY_NOT_MULTIPLE_OF_SALE_UNIT);
    }

    @Test
    @DisplayName("생성 — 설명을 함께 저장한다")
    void createUnitProduct_savesDescription() {
        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "SIGNERS", "서명자", "행사장 서명자 한도를 늘립니다.", "ESSENTIAL", null, null, null, null, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true,
                null, null, null
        );

        UnitProductDto.Response.UnitProductSummary result = unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request);

        assertThat(result.getDescription()).isEqualTo("행사장 서명자 한도를 늘립니다.");
    }

    @Test
    @DisplayName("생성 — platformUsageFee를 생략하면 카테고리로부터 기본값을 계산한다")
    void createUnitProduct_platformUsageFeeOmitted_defaultsFromCategory() {
        UnitProductDto.Request.CreateUnitProduct essential = new UnitProductDto.Request.CreateUnitProduct(
                "SIGNERS", "서명자", null, "ESSENTIAL", null, null, null, null, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true, null, null, null
        );
        UnitProductDto.Request.CreateUnitProduct personnel = new UnitProductDto.Request.CreateUnitProduct(
                "ONSITE_SUPPORT", "현장지원", null, "PERSONNEL", null, null, null, null, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true, null, null, null
        );

        UnitProductDto.Response.UnitProductSummary essentialResult = unitProductService.createUnitProduct("PLATFORM_OPS", 1L, essential);
        UnitProductDto.Response.UnitProductSummary personnelResult = unitProductService.createUnitProduct("PLATFORM_OPS", 1L, personnel);

        assertThat(essentialResult.isPlatformUsageFee()).isTrue();
        assertThat(personnelResult.isPlatformUsageFee()).isFalse();
    }

    @Test
    @DisplayName("생성 — platformUsageFee를 명시하면 카테고리와 달라도 그 값을 그대로 쓴다")
    void createUnitProduct_platformUsageFeeExplicit_overridesCategoryDefault() {
        UnitProductDto.Request.CreateUnitProduct request = new UnitProductDto.Request.CreateUnitProduct(
                "ONSITE_SUPPORT", "현장지원(요청형·관리자 견적)", null, "PERSONNEL", null, null, null, true, "KRW",
                java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(20), "KR_VAT_STANDARD", true, null, null, null
        );

        UnitProductDto.Response.UnitProductSummary result = unitProductService.createUnitProduct("PLATFORM_OPS", 1L, request);

        assertThat(result.isPlatformUsageFee()).isTrue();
    }

    @Test
    @DisplayName("수정 — platformUsageFee를 명시한 값으로 덮어쓴다")
    void updateUnitProduct_overridesPlatformUsageFee() {
        UnitProduct product = unitProduct();
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(product));

        UnitProductDto.Request.UpdateUnitProduct request =
                new UnitProductDto.Request.UpdateUnitProduct("서명자", null, "PERSONNEL", null, null, null, true, null);

        UnitProductDto.Response.UnitProductSummary result =
                unitProductService.updateUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L, request);

        assertThat(result.isPlatformUsageFee()).isTrue();
    }

    @Test
    @DisplayName("수정 — 설명을 갱신한다")
    void updateUnitProduct_updatesDescription() {
        UnitProduct product = unitProduct();
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(product));

        UnitProductDto.Request.UpdateUnitProduct request =
                new UnitProductDto.Request.UpdateUnitProduct("서명자", "새 설명", "ESSENTIAL", null, null, null, true, null);

        UnitProductDto.Response.UnitProductSummary result =
                unitProductService.updateUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L, request);

        assertThat(result.getDescription()).isEqualTo("새 설명");
    }

    @Test
    @DisplayName("수정 — effectDefinitionIds를 생략하면(null) 기존 구성을 건드리지 않는다")
    void updateUnitProduct_keepsExistingWhenEffectDefinitionIdsOmitted() {
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", UNIT_PRODUCT_ID);
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(bundle));

        UnitProductDto.Request.UpdateUnitProduct request =
                new UnitProductDto.Request.UpdateUnitProduct("3종 묶음", null, "APPLICATION", null, null, null, true, null);

        unitProductService.updateUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L, request);

        verify(unitProductEffectPort, never()).replaceEffectDefinitions(any(), any());
    }

    @Test
    @DisplayName("수정 — effectDefinitionIds를 명시하면 효과 연결을 통째로 교체한다")
    void updateUnitProduct_replacesEffectDefinitions() {
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", UNIT_PRODUCT_ID);
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(bundle));

        UnitProductDto.Request.UpdateUnitProduct request =
                new UnitProductDto.Request.UpdateUnitProduct("3종 묶음", null, "APPLICATION", null, null, null, true, java.util.List.of(10L));

        unitProductService.updateUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L, request);

        verify(unitProductEffectPort).replaceEffectDefinitions(bundle, java.util.List.of(10L));
    }

    @Test
    @DisplayName("수정 — 빈 배열을 명시하면 전부 해제하고 새로 만들지 않는다")
    void updateUnitProduct_clearsEffectDefinitionOptionsOnEmptyList() {
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", UNIT_PRODUCT_ID);
        given(unitProductRepository.findById(UNIT_PRODUCT_ID)).willReturn(Optional.of(bundle));

        UnitProductDto.Request.UpdateUnitProduct request =
                new UnitProductDto.Request.UpdateUnitProduct("3종 묶음", null, "APPLICATION", null, null, null, true, java.util.List.of());

        unitProductService.updateUnitProduct(UNIT_PRODUCT_ID, "PLATFORM_OPS", 1L, request);

        verify(unitProductEffectPort).replaceEffectDefinitions(bundle, java.util.List.of());
    }

    @Test
    @DisplayName("순서 변경 — 요청에 담긴 각 상품의 displayOrder를 그대로 반영한다")
    void updateDisplayOrders_updatesEachProduct() {
        UnitProduct first = unitProduct();
        UnitProduct second = UnitProduct.builder()
                .type(UnitProductType.TEMPLATES).name("템플릿").category(UnitProductCategory.ESSENTIAL).build();
        ReflectionTestUtils.setField(second, "id", 902L);
        given(unitProductRepository.findAllById(java.util.List.of(UNIT_PRODUCT_ID, 902L)))
                .willReturn(java.util.List.of(first, second));
        given(unitProductRepository.findAllByOrderByDisplayOrderAscIdAsc()).willReturn(java.util.List.of(second, first));

        DisplayOrderRequest.UpdateDisplayOrders request = new DisplayOrderRequest.UpdateDisplayOrders(java.util.List.of(
                new DisplayOrderRequest.Item(UNIT_PRODUCT_ID, 1),
                new DisplayOrderRequest.Item(902L, 0)
        ));

        var result = unitProductService.updateDisplayOrders("PLATFORM_OPS", 1L, request);

        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isEqualTo(0);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("순서 변경 — 존재하지 않는 id가 섞여 있으면 UNIT_PRODUCT_NOT_FOUND")
    void updateDisplayOrders_throwsWhenNotFound() {
        given(unitProductRepository.findAllById(java.util.List.of(UNIT_PRODUCT_ID))).willReturn(java.util.List.of());

        DisplayOrderRequest.UpdateDisplayOrders request = new DisplayOrderRequest.UpdateDisplayOrders(
                java.util.List.of(new DisplayOrderRequest.Item(UNIT_PRODUCT_ID, 0))
        );

        assertThatThrownBy(() -> unitProductService.updateDisplayOrders("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(BillingErrorCode.UNIT_PRODUCT_NOT_FOUND);
    }
}
