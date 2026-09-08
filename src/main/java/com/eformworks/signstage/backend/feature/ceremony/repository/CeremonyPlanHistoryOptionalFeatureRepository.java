package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryOptionalFeature;
import java.util.List;

public interface CeremonyPlanHistoryOptionalFeatureRepository extends AppendOnlyRepository<CeremonyPlanHistoryOptionalFeature, Long> {

    /** "그 스냅샷 시점에 플랜에 포함돼 있던 선택옵션" 조회용. */
    List<CeremonyPlanHistoryOptionalFeature> findAllByCeremonyPlanHistoryId(Long ceremonyPlanHistoryId);
}
