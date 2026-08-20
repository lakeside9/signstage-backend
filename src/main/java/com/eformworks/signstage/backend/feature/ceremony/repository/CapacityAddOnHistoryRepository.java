package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapacityAddOnHistoryRepository extends JpaRepository<CapacityAddOnHistory, Long> {

    /** 이력 조회용(최신순). */
    List<CapacityAddOnHistory> findAllByCapacityAddOnIdOrderByCreatedAtDesc(Long capacityAddOnId);
}
