package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnHistory;
import java.util.List;

public interface CapacityAddOnHistoryRepository extends AppendOnlyRepository<CapacityAddOnHistory, Long> {

    /** 이력 조회용(최신순). */
    List<CapacityAddOnHistory> findAllByCapacityAddOnIdOrderByCreatedAtDesc(Long capacityAddOnId);
}
