package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionalFeatureRepository extends JpaRepository<OptionalFeature, Long> {

    boolean existsByCode(OptionalFeatureCode code);

    List<OptionalFeature> findAllByIdIn(List<Long> ids);
}
