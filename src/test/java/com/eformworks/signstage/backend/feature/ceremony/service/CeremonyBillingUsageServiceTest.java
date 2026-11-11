package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link CeremonyBillingUsageService} 단위 테스트 — 과금 카탈로그 삭제 가능 여부를 정하는 행사 쪽
 * 사용 이력 판정. 2026-11-11 feature.billing 분리 전에는 {@code BillingPlanServiceTest}/
 * {@code UnitProductServiceTest}가 행사 Repository를 직접 mock해 확인하던 시나리오다.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyBillingUsageServiceTest {

    @Mock
    private CeremonyRepository ceremonyRepository;
    @Mock
    private CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    @Mock
    private CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    @Mock
    private CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    @Mock
    private CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    @Mock
    private CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;

    @InjectMocks
    private CeremonyBillingUsageService ceremonyBillingUsageService;

    private static final Long PLAN_ID = 101L;
    private static final Long UNIT_PRODUCT_ID = 901L;

    @Test
    @DisplayName("플랜 — 지금 이 플랜을 쓰는 행사가 있으면 사용 중이다")
    void isBillingPlanUsed_trueWhenLiveCeremonyUsesPlan() {
        given(ceremonyRepository.existsByBillingPlanId(PLAN_ID)).willReturn(true);

        assertThat(ceremonyBillingUsageService.isBillingPlanUsed(PLAN_ID)).isTrue();
    }

    @Test
    @DisplayName("플랜 — 과거 행사에 스냅샷된 적만 있어도(현재 플랜 구성과 무관) 사용 중이다")
    void isBillingPlanUsed_trueWhenEverSnapshotted() {
        given(ceremonyRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(ceremonyPlanHistoryRepository.existsByBillingPlanId(PLAN_ID)).willReturn(true);

        assertThat(ceremonyBillingUsageService.isBillingPlanUsed(PLAN_ID)).isTrue();
    }

    @Test
    @DisplayName("플랜 — 행사와 스냅샷 어디에도 없으면 사용 중이 아니다")
    void isBillingPlanUsed_falseWhenNeverUsed() {
        given(ceremonyRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);
        given(ceremonyPlanHistoryRepository.existsByBillingPlanId(PLAN_ID)).willReturn(false);

        assertThat(ceremonyBillingUsageService.isBillingPlanUsed(PLAN_ID)).isFalse();
    }

    @Test
    @DisplayName("단위 상품 — 과거 행사에 스냅샷된 적이 있으면 사용 중이다")
    void isUnitProductUsed_trueWhenEverSnapshotted() {
        given(ceremonyPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(true);

        assertThat(ceremonyBillingUsageService.isUnitProductUsed(UNIT_PRODUCT_ID)).isTrue();
    }

    @Test
    @DisplayName("단위 상품 — 효과 묶음에 연결돼 있으면 사용 중이다")
    void isUnitProductUsed_trueWhenLinkedToEffectDefinition() {
        given(ceremonyPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyUnitProductPurchaseLineRepository.existsByUnitProduct_Id(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyEventOptionalFeatureRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyEffectDefinitionOptionRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(true);

        assertThat(ceremonyBillingUsageService.isUnitProductUsed(UNIT_PRODUCT_ID)).isTrue();
    }

    @Test
    @DisplayName("단위 상품 — 스냅샷·구매·옵션 기능·효과 묶음 어디에도 없으면 사용 중이 아니다")
    void isUnitProductUsed_falseWhenNeverUsed() {
        given(ceremonyPlanHistoryUnitProductRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyUnitProductPurchaseLineRepository.existsByUnitProduct_Id(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyEventOptionalFeatureRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);
        given(ceremonyEffectDefinitionOptionRepository.existsByUnitProductId(UNIT_PRODUCT_ID)).willReturn(false);

        assertThat(ceremonyBillingUsageService.isUnitProductUsed(UNIT_PRODUCT_ID)).isFalse();
    }

    @Test
    @DisplayName("단위 상품 — 구매 건수는 승인(APPROVED)된 것만 센다")
    void countApprovedPurchases_countsApprovedOnly() {
        given(ceremonyUnitProductPurchaseLineRepository.countByUnitProduct_IdAndPurchase_Status(
                UNIT_PRODUCT_ID, PurchaseStatus.APPROVED
        )).willReturn(3L);

        assertThat(ceremonyBillingUsageService.countApprovedPurchases(UNIT_PRODUCT_ID)).isEqualTo(3L);
    }
}
