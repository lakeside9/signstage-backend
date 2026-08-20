package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionalFeatureHistoryRepository extends JpaRepository<OptionalFeatureHistory, Long> {

    /** 이력 조회용(최신순). */
    List<OptionalFeatureHistory> findAllByOptionalFeatureIdOrderByCreatedAtDesc(Long optionalFeatureId);
}
