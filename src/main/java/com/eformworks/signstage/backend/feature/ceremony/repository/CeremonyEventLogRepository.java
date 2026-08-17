package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.ActorType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyEventLogRepository extends JpaRepository<CeremonyEventLog, Long> {

    List<CeremonyEventLog> findAllByCeremonyEventId(Long ceremonyEventId);

    boolean existsByCeremonyEventIdAndActorTypeAndActorIdAndEventAction(
            Long ceremonyEventId,
            ActorType actorType,
            Long actorId,
            CeremonyEventAction eventAction
    );
}
