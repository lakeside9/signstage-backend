package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CeremonyDeletionService} 단위 테스트 — 삭제 가능 조건과 자식 데이터 연쇄 삭제.
 * 2026-11-11 {@code CeremonyServiceTest}에서 분리했다. 공용 mock과 서비스 조립은
 * {@link CeremonyServiceTestSupport}에 있다.
 */
class CeremonyDeletionServiceTest extends CeremonyServiceTestSupport {

    /**
     * {@link CeremonyDeletionService#deleteCeremony}의 "플랜이 확정되지 않은(DRAFT) 행사만 삭제 가능"
     * 규칙 단위 테스트 — signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 11장, 2026-09-10 사용자
     * 요청.
     */
    @Test
    @DisplayName("삭제 — DRAFT이고 대기중/승인된 추가구매가 없으면 자신의 이력·추가구매·장바구니·배정·문의·현장지원 요청을 함께 지운다")
    void deleteCeremony_deletesWhenDraftAndNoActivity() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        stubOwnerMember(ceremony);
        given(ceremonyUnitProductPurchaseRepository.existsByCeremonyIdAndStatusIn(
                CEREMONY_ID, List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)
        )).willReturn(false);

        ceremonyDeletionService.deleteCeremony(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID);

        verify(ceremonyPlanHistoryUnitProductRepository).deleteAllByCeremonyPlanHistory_CeremonyId(CEREMONY_ID);
        verify(ceremonyPlanHistoryRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyUnitProductPurchaseLineRepository).deleteAllByPurchase_CeremonyId(CEREMONY_ID);
        verify(ceremonyUnitProductPurchaseRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyUnitProductCartLineRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyAssignmentRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyInquiryMessageRepository).deleteAllByInquiry_CeremonyId(CEREMONY_ID);
        verify(ceremonyInquiryRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyOnsiteSupportRequestRepository).deleteAllByCeremonyId(CEREMONY_ID);
        verify(ceremonyRepository).delete(ceremony);
    }

    @Test
    @DisplayName("삭제 — 플랜이 확정된(IN_PROGRESS) 행사는 거부하고 아무것도 지우지 않는다")
    void deleteCeremony_rejectsWhenPlanConfirmed() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        ceremony.confirmPlan();
        stubOwnerMember(ceremony);

        assertThatThrownBy(() -> ceremonyDeletionService.deleteCeremony(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
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

        assertThatThrownBy(() -> ceremonyDeletionService.deleteCeremony(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_NOT_DELETABLE);

        verify(ceremonyRepository, never()).delete(any(Ceremony.class));
    }
}
