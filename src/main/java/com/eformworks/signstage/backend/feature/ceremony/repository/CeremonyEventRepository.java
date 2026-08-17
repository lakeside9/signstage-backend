package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyEventRepository extends JpaRepository<CeremonyEvent, Long> {

    List<CeremonyEvent> findAllByCeremonyId(Long ceremonyId);

    long countByCeremonyIdAndEventType(Long ceremonyId, CeremonyEventType eventType);

    boolean existsByAccessKey(String accessKey);
}
