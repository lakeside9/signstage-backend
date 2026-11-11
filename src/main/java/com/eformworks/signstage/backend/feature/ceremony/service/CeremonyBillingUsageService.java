package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.feature.billing.port.CeremonyBillingUsagePort;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CeremonyBillingUsagePort} 구현 — 과금 카탈로그 삭제 가능 여부와 사용 건수를 billing에
 * 알려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyBillingUsageService implements CeremonyBillingUsagePort {

    private final CeremonyRepository ceremonyRepository;
    private final CeremonyPlanHistoryRepository ceremonyPlanHistoryRepository;
    private final CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    private final CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;

    @Override
    public boolean isBillingPlanUsed(Long billingPlanId) {
        return ceremonyRepository.existsByBillingPlanId(billingPlanId)
                || ceremonyPlanHistoryRepository.existsByBillingPlanId(billingPlanId);
    }

    @Override
    public long countCeremoniesByBillingPlan(Long billingPlanId) {
        return ceremonyRepository.countByBillingPlanId(billingPlanId);
    }

    @Override
    public boolean isUnitProductUsed(Long unitProductId) {
        return ceremonyPlanHistoryUnitProductRepository.existsByUnitProductId(unitProductId)
                || ceremonyUnitProductPurchaseLineRepository.existsByUnitProduct_Id(unitProductId)
                || ceremonyEventOptionalFeatureRepository.existsByUnitProductId(unitProductId)
                || ceremonyEffectDefinitionOptionRepository.existsByUnitProductId(unitProductId);
    }

    @Override
    public long countApprovedPurchases(Long unitProductId) {
        return ceremonyUnitProductPurchaseLineRepository.countByUnitProduct_IdAndPurchase_Status(
                unitProductId, PurchaseStatus.APPROVED
        );
    }

    @Override
    public long countCeremoniesBySubscription(Long subscriptionId) {
        return ceremonyRepository.countBySubscriptionId(subscriptionId);
    }
}
