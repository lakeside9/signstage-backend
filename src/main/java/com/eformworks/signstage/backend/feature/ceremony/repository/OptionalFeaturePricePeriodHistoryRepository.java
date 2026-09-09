package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeaturePricePeriodHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionalFeaturePricePeriodHistoryRepository extends JpaRepository<OptionalFeaturePricePeriodHistory, Long> {

    List<OptionalFeaturePricePeriodHistory> findAllByOptionalFeatureIdOrderByCreatedAtDesc(Long optionalFeatureId);
}
