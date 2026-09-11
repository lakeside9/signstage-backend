package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyAssignmentRepository extends JpaRepository<CeremonyAssignment, Long> {

    boolean existsByCeremonyIdAndUserId(Long ceremonyId, Long userId);

    List<CeremonyAssignment> findAllByUserId(Long userId);

    /** 플랜이 확정되지 않은(DRAFT) 행사 삭제 시 이 행사에 걸린 담당자 배정을 함께 지운다. */
    void deleteAllByCeremonyId(Long ceremonyId);
}
