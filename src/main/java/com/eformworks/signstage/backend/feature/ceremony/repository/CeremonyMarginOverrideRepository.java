package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyMarginOverride;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyMarginOverrideRepository extends JpaRepository<CeremonyMarginOverride, Long> {

    Optional<CeremonyMarginOverride> findByCeremonyId(Long ceremonyId);
}
