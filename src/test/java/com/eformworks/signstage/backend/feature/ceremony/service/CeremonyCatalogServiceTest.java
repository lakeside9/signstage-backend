package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyCatalogService} 단위 테스트 — 유효 한도(승인된 구매 줄만 반영), 추가구매 가능 단위 상품(DRAFT는 라이브, 확정 후 스냅샷), 배타 그룹 검사.
 * 2026-11-11 {@code CeremonyServiceTest}에서 분리했다. 공용 mock과 서비스 조립은
 * {@link CeremonyServiceTestSupport}에 있다.
 */
class CeremonyCatalogServiceTest extends CeremonyServiceTestSupport {

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
        int effective = ceremonyCatalogService.calculateEffectiveCapacity(ceremony, UnitProductType.TABLETS);

        // then — 플랜 기본 포함 0(TABLETS는 플랜 기본값 개념이 없다) + 승인된 구매 줄 합(10+5)
        assertThat(effective).isEqualTo(15);
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

        List<Long> result = ceremonyCatalogService.retrievePurchasableUnitProductIds(ceremony);

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

        List<Long> result = ceremonyCatalogService.retrievePurchasableUnitProductIds(ceremony);

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

        List<Long> result = ceremonyCatalogService.retrievePurchasableUnitProductIds(ceremony);

        assertThat(result).containsExactly(901L);
    }

    /**
     * 2026-09-12 사용자 지적 — "이벤트 효과 묶음 단위상품을 행사에서 이미 구매를 한 상태이면
     * 추가 구매도 안되도록 구현이 되어 있는지 확인해주세요. 과금플랜에 포함되어 있어도 추가
     * 구매가 안되도록 제약이 필요합니다." 확인해보니 후자(플랜 기본 포함분)는 막혀 있지
     * 않았다 — retrieveApplicableUnitProductIds는 "플랜 기본 포함만으로도 적용 가능"이라고
     * 이미 인정하는데, 추가구매 후보 목록엔 그 판단이 반영되지 않아 같은 효과를 또 살 수
     * 있었다.
     */
    @Test
    @DisplayName("추가구매 후보 목록 — 이벤트 효과 묶음(토글형)이 플랜에 이미 포함(1개)돼 있으면 후보에서 뺀다")
    void retrievePurchasableUnitProductIds_excludesToggleAlreadyIncludedInPlan() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("플랜").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);

        UnitProduct signers = unitProduct(901L, UnitProductType.SIGNERS); // 수량형 — 기본 포함이어도 추가구매 가능해야 한다.
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 902L);
        BillingPlanUnitProduct signersLine = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(signers).includedQuantity(5).build();
        BillingPlanUnitProduct bundleLine = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(bundle).includedQuantity(1).build();
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(signersLine, bundleLine));

        List<Long> result = ceremonyCatalogService.retrievePurchasableUnitProductIds(ceremony);

        assertThat(result).containsExactly(901L);
    }

    @Test
    @DisplayName("추가구매 후보 목록 — 이벤트 효과 묶음이 플랜에 0개 포함(구매로만 확보)이면 후보에 남는다")
    void retrievePurchasableUnitProductIds_includesToggleWhenIncludedQuantityIsZero() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("플랜").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", 10L);

        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 902L);
        BillingPlanUnitProduct bundleLine = BillingPlanUnitProduct.builder()
                .billingPlan(plan).unitProduct(bundle).includedQuantity(0).build();
        given(billingPlanUnitProductRepository.findAllByBillingPlanId(101L)).willReturn(List.of(bundleLine));

        List<Long> result = ceremonyCatalogService.retrievePurchasableUnitProductIds(ceremony);

        assertThat(result).containsExactly(902L);
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

        assertThatThrownBy(() -> ceremonyCatalogService.checkExclusivityGroups(List.of(near, far)))
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

        assertThatCode(() -> ceremonyCatalogService.checkExclusivityGroups(List.of(near, tablet, onlineSupport)))
                .doesNotThrowAnyException();
    }
}
