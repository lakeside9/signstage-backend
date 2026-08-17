package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyResultRepository extends JpaRepository<CeremonyResult, Long> {

    List<CeremonyResult> findAllByCeremonyEventId(Long ceremonyEventId);

    boolean existsByCeremonyEventId(Long ceremonyEventId);
}
