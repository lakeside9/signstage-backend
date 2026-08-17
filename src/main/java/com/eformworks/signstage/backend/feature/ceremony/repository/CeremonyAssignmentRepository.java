package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyAssignmentRepository extends JpaRepository<CeremonyAssignment, Long> {

    boolean existsByCeremonyIdAndUserId(Long ceremonyId, Long userId);

    List<CeremonyAssignment> findAllByUserId(Long userId);
}
