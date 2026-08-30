package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryCapacityAddOn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyPlanHistoryCapacityAddOnRepository extends JpaRepository<CeremonyPlanHistoryCapacityAddOn, Long> {

    /** "그 스냅샷 시점에 플랜에서 구매 가능했던 용량 추가구매 상품" 조회용. */
    List<CeremonyPlanHistoryCapacityAddOn> findAllByCeremonyPlanHistoryId(Long ceremonyPlanHistoryId);
}
