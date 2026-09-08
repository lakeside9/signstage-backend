package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureHistory;
import java.util.List;

public interface OptionalFeatureHistoryRepository extends AppendOnlyRepository<OptionalFeatureHistory, Long> {

    /** 이력 조회용(최신순). */
    List<OptionalFeatureHistory> findAllByOptionalFeatureIdOrderByCreatedAtDesc(Long optionalFeatureId);
}
