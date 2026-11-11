package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyInquiryMessageRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyInquiryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOnsiteSupportRequestRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductCartLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DRAFT 행사 삭제와 그 자식 데이터 연쇄 삭제. 삭제에만 쓰는 repository가 많아
 * {@link CeremonyService}에서 분리했다(backend-package-structure-consistency-review.md 11.6절).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyDeletionService {

    private final CeremonyRepository ceremonyRepository;
    private final CeremonyAssignmentRepository ceremonyAssignmentRepository;
    private final CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;
    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    private final CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    private final CeremonyUnitProductCartLineRepository ceremonyUnitProductCartLineRepository;
    private final CeremonyInquiryRepository ceremonyInquiryRepository;
    private final CeremonyInquiryMessageRepository ceremonyInquiryMessageRepository;
    private final CeremonyOnsiteSupportRequestRepository ceremonyOnsiteSupportRequestRepository;
    private final CeremonyAccessService ceremonyAccessService;

    /**
     * 플랜이 확정되지 않은(DRAFT) 행사만 삭제할 수 있다(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 11장, 2026-09-10 사용자
     * 요청). DRAFT는 플랜 확정 전 상태라 서명자/문서/하위 행사 등록 자체가 막혀 있어
     * ({@code checkCeremonyPlanConfirmed}, {@link SignerService}/{@link TemplateService}/
     * {@link CeremonyEventService}가 등록 시점에 강제) 항상 비어 있다. 다만 단위 상품
     * 추가구매(안 A, {@link CeremonyUnitProductPurchaseService#purchaseUnitProducts})는 DRAFT 상태에서도 만들 수 있어서, 대기중·
     * 승인된 추가구매가 있으면 거부한다 — 반려(REJECTED)된 추가구매만 있으면 막지 않는다
     * (이미 종결된 이력일 뿐이라 재요청 허용 판정과 같은 기준). "확정 견적"({@code BillingQuote})
     * 기능은 자가-체크아웃 도입으로 완전히 제거됐다(signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 6장 결정, 2026-09-11).
     *
     * <p>통과하면 이 행사 자신의 플랜 선택 이력(+ 그 안의 단위 상품 스냅샷)·추가구매 요청(+ 그
     * 줄)·장바구니·담당자 배정까지 함께 지운다 — DB에 {@code ON DELETE CASCADE}가 없어 자식부터
     * 순서대로 지운다({@code UnitProductService#deleteUnitProduct}와 같은 패턴).
     */
    @Transactional
    public void deleteCeremony(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyAccessService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyAccessService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyAccessService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        checkCeremonyDeletable(ceremony);

        ceremonyPlanHistoryUnitProductRepository.deleteAllByCeremonyPlanHistory_CeremonyId(ceremonyId);
        ceremonyPlanHistoryRepository.deleteAllByCeremonyId(ceremonyId);
        ceremonyUnitProductPurchaseLineRepository.deleteAllByPurchase_CeremonyId(ceremonyId);
        ceremonyUnitProductPurchaseRepository.deleteAllByCeremonyId(ceremonyId);
        ceremonyUnitProductCartLineRepository.deleteAllByCeremonyId(ceremonyId);
        ceremonyAssignmentRepository.deleteAllByCeremonyId(ceremonyId);
        ceremonyInquiryMessageRepository.deleteAllByInquiry_CeremonyId(ceremonyId);
        ceremonyInquiryRepository.deleteAllByCeremonyId(ceremonyId);
        ceremonyOnsiteSupportRequestRepository.deleteAllByCeremonyId(ceremonyId);
        ceremonyRepository.delete(ceremony);
    }

    private void checkCeremonyDeletable(Ceremony ceremony) {
        if (ceremony.getStatus() != CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_NOT_DELETABLE);
        }
        boolean hasActivePurchase = ceremonyUnitProductPurchaseRepository.existsByCeremonyIdAndStatusIn(
                ceremony.getId(), List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)
        );
        if (hasActivePurchase) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_NOT_DELETABLE);
        }
    }
}
