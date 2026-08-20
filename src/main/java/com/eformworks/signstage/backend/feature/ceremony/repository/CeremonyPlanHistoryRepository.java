package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyPlanHistoryRepository extends JpaRepository<CeremonyPlanHistory, Long> {

    /** 이력 조회용(최신순) — 가장 앞 행이 확정/현재 시점에 가장 가까운 변경이다. */
    List<CeremonyPlanHistory> findAllByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);

    /**
     * "지금 이 행사가 쓰는 플랜 조건"의 근거 — 카탈로그(BillingPlan)가 나중에 바뀌어도 이 스냅샷은
     * 안 바뀐다. {@code CeremonyService#calculateEffectiveCapacity}가 라이브 조회 대신 이걸 쓴다.
     */
    Optional<CeremonyPlanHistory> findFirstByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);
}
