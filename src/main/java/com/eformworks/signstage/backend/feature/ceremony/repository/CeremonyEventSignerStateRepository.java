package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventSignerState;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventSignerStateId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyEventSignerStateRepository
        extends JpaRepository<CeremonyEventSignerState, CeremonyEventSignerStateId> {

    Optional<CeremonyEventSignerState> findByIdEventIdAndIdSignerId(Long eventId, Long signerId);

    List<CeremonyEventSignerState> findAllByIdEventId(Long eventId);
}
