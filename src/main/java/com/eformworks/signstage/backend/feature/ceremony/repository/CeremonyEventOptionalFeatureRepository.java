package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventOptionalFeature;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyEventOptionalFeatureRepository extends JpaRepository<CeremonyEventOptionalFeature, Long> {

    List<CeremonyEventOptionalFeature> findAllByCeremonyEventId(Long ceremonyEventId);

    void deleteAllByCeremonyEventId(Long ceremonyEventId);
}
