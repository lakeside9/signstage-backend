package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductHistory;
import java.util.List;

public interface UnitProductHistoryRepository extends AppendOnlyRepository<UnitProductHistory, Long> {

    /** 이력 조회용(최신순). */
    List<UnitProductHistory> findAllByUnitProductIdOrderByCreatedAtDesc(Long unitProductId);
}
