package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.ceremony.dto.PlatformAdminCeremonyPurchaseDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyUnitProductPurchaseApprovalService} 단위 테스트 — 승인된 구매의 취소와 이벤트 효과 묶음 적용 해제.
 * 2026-11-11 {@code CeremonyServiceTest}에서 분리했다. 공용 mock과 서비스 조립은
 * {@link CeremonyServiceTestSupport}에 있다.
 */
class CeremonyUnitProductPurchaseApprovalServiceTest extends CeremonyServiceTestSupport {

    /**
     * 2026-09-12 사용자 요청 — "파트너가 구매한 것에 대해 플랫폼 관리자가 취소할 수 있게
     * 하는 것을 검토해주세요" 구현. signstage-docs
     * business/ceremony-unit-product-purchase-cancellation-review.md 결정.
     */
    @Test
    @DisplayName("구매 취소 — 승인된 구매를 사유와 함께 취소한다(이벤트 효과 묶음 아님)")
    void cancelUnitProductPurchase_approvedNonBundlePurchase_cancels() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        UnitProduct signers = unitProduct(901L, UnitProductType.SIGNERS);

        CeremonyUnitProductPurchase purchase = CeremonyUnitProductPurchase.builder().ceremony(ceremony).build();
        ReflectionTestUtils.setField(purchase, "id", 501L);
        ReflectionTestUtils.setField(purchase, "status", PurchaseStatus.APPROVED);

        CeremonyUnitProductPurchaseLine line = CeremonyUnitProductPurchaseLine.builder()
                .purchase(purchase).unitProduct(signers).quantity(2).currencyCode("KRW")
                .purchasedName("서명자").purchasedSalePrice(new BigDecimal("1000")).purchasedTaxCode("KR_VAT_STANDARD")
                .build();

        given(ceremonyUnitProductPurchaseRepository.findById(501L)).willReturn(Optional.of(purchase));
        given(ceremonyUnitProductPurchaseLineRepository.findAllByPurchaseIdOrderByIdAsc(501L)).willReturn(List.of(line));
        given(ceremonyEventRepository.findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(10L)).willReturn(List.of());

        PlatformAdminCeremonyPurchaseDto.Request.Cancel request =
                new PlatformAdminCeremonyPurchaseDto.Request.Cancel("파트너 요청으로 정정");

        PlatformAdminCeremonyPurchaseDto.Response.UnitProductPurchaseRequestSummary result =
                ceremonyUnitProductPurchaseApprovalService.cancelUnitProductPurchase(501L, 999L, "PLATFORM_OPS", request);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getCancellationReason()).isEqualTo("파트너 요청으로 정정");
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(purchase.getCancelledBy()).isEqualTo(999L);
        // 이벤트 효과 묶음이 아니므로 자동 해제 경로 자체를 타지 않아야 한다.
        verify(ceremonyEventOptionalFeatureRepository, never()).findAllByCeremonyEventId(any());
    }

    @Test
    @DisplayName("구매 취소 — PENDING 구매는 취소할 수 없다")
    void cancelUnitProductPurchase_notApproved_rejected() {
        CeremonyUnitProductPurchase purchase = CeremonyUnitProductPurchase.builder()
                .ceremony(ceremony(organization(), 10L)).build();
        ReflectionTestUtils.setField(purchase, "id", 502L);
        // builder 기본값이 PENDING이라 별도 설정 불필요.

        given(ceremonyUnitProductPurchaseRepository.findById(502L)).willReturn(Optional.of(purchase));

        PlatformAdminCeremonyPurchaseDto.Request.Cancel request = new PlatformAdminCeremonyPurchaseDto.Request.Cancel("사유");

        assertThatThrownBy(() -> ceremonyUnitProductPurchaseApprovalService.cancelUnitProductPurchase(502L, 999L, "PLATFORM_OPS", request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_PURCHASE_NOT_APPROVED);
    }

    @Test
    @DisplayName("구매 취소 — 이벤트 효과 묶음은 STARTED가 아닌 하위 행사에서만 자동 해제된다")
    void cancelUnitProductPurchase_eventEffectBundle_unappliesFromNonStartedEventsOnly() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 902L);

        CeremonyUnitProductPurchase purchase = CeremonyUnitProductPurchase.builder().ceremony(ceremony).build();
        ReflectionTestUtils.setField(purchase, "id", 503L);
        ReflectionTestUtils.setField(purchase, "status", PurchaseStatus.APPROVED);

        CeremonyUnitProductPurchaseLine line = CeremonyUnitProductPurchaseLine.builder()
                .purchase(purchase).unitProduct(bundle).quantity(1).currencyCode("KRW")
                .purchasedName("3종 묶음").purchasedSalePrice(new BigDecimal("5000")).purchasedTaxCode("KR_VAT_STANDARD")
                .build();

        CeremonyEvent startedEvent = CeremonyEvent.builder()
                .ceremony(ceremony).name("본행사").eventType(CeremonyEventType.MAIN).accessKey("key-1").build();
        ReflectionTestUtils.setField(startedEvent, "id", 701L);
        ReflectionTestUtils.setField(startedEvent, "status", CeremonyEventStatus.STARTED);

        CeremonyEvent draftEvent = CeremonyEvent.builder()
                .ceremony(ceremony).name("테스트").eventType(CeremonyEventType.TEST).accessKey("key-2").build();
        ReflectionTestUtils.setField(draftEvent, "id", 702L);
        // 기본값 DRAFT — 별도 설정 불필요.

        CeremonyEventOptionalFeature draftMapping = CeremonyEventOptionalFeature.builder()
                .ceremonyEvent(draftEvent).unitProduct(bundle).build();

        given(ceremonyUnitProductPurchaseRepository.findById(503L)).willReturn(Optional.of(purchase));
        given(ceremonyUnitProductPurchaseLineRepository.findAllByPurchaseIdOrderByIdAsc(503L)).willReturn(List.of(line));
        given(ceremonyEventRepository.findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(10L))
                .willReturn(List.of(startedEvent, draftEvent));
        given(ceremonyEventOptionalFeatureRepository.findAllByCeremonyEventId(702L)).willReturn(List.of(draftMapping));
        given(ceremonyEventEffectSettingRepository.findAllByEventIdWithDefinition(702L)).willReturn(List.of());

        ceremonyUnitProductPurchaseApprovalService.cancelUnitProductPurchase(
                503L, 999L, "PLATFORM_OPS", new PlatformAdminCeremonyPurchaseDto.Request.Cancel("사유")
        );

        // STARTED인 하위 행사는 조회조차 하지 않는다(효율적 단락 평가) — 자동 해제 대상에서 빠진다.
        verify(ceremonyEventOptionalFeatureRepository, never()).findAllByCeremonyEventId(701L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CeremonyEventOptionalFeature>> captor = ArgumentCaptor.forClass(List.class);
        verify(ceremonyEventOptionalFeatureRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(draftMapping);
    }
}
