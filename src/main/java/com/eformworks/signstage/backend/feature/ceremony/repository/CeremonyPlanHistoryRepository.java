package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyPlanHistoryRepository extends JpaRepository<CeremonyPlanHistory, Long> {

    /** 이력 조회용(최신순) — 가장 앞 행이 확정/현재 시점에 가장 가까운 변경이다. */
    List<CeremonyPlanHistory> findAllByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);
}
