package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryCapacity;
import java.util.List;
import java.util.Optional;

public interface CeremonyPlanHistoryCapacityRepository extends AppendOnlyRepository<CeremonyPlanHistoryCapacity, Long> {

    /** "그 스냅샷 시점에 플랜이 기본 포함하던 용량 한도" 조회용. */
    List<CeremonyPlanHistoryCapacity> findAllByCeremonyPlanHistoryId(Long ceremonyPlanHistoryId);

    Optional<CeremonyPlanHistoryCapacity> findByCeremonyPlanHistoryIdAndCapacityType(
            Long ceremonyPlanHistoryId, CapacityType capacityType
    );
}
