package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CeremonyPlanHistoryRepository extends AppendOnlyRepository<CeremonyPlanHistory, Long> {

    /** 이력 조회용(최신순) — 가장 앞 행이 확정/현재 시점에 가장 가까운 변경이다. */
    List<CeremonyPlanHistory> findAllByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);

    /**
     * "지금 이 행사가 쓰는 플랜 조건"의 근거 — 카탈로그(BillingPlan)가 나중에 바뀌어도 이 스냅샷은
     * 안 바뀐다. {@code CeremonyService#calculateEffectiveCapacity}가 라이브 조회 대신 이걸 쓴다.
     */
    Optional<CeremonyPlanHistory> findFirstByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);

    /**
     * 과금 플랜 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 지금 플랜 구성과 무관하게 과거
     * 어느 행사든 이 플랜을 스냅샷한 적이 있는지({@link CeremonyRepository#existsByBillingPlanId}는
     * "지금 쓰는 행사"만 본다, 이건 그와 별개로 "한 번이라도 쓴 적"까지 본다).
     */
    boolean existsByBillingPlanId(Long billingPlanId);

    /**
     * 플랜이 확정되지 않은(DRAFT) 행사 삭제({@code CeremonyService#deleteCeremony}) 시 이
     * 행사 자신의 플랜 선택 이력을 함께 지운다 — {@code AppendOnlyRepository}는 delete를
     * 노출하지 않지만 커스텀 쿼리는 예외다({@code UnitProductHistoryRepository.deleteAllByUnitProductId}
     * 와 같은 패턴). 호출 전
     * {@code CeremonyPlanHistoryUnitProductRepository.deleteAllByCeremonyPlanHistory_CeremonyId}
     * 로 자식 스냅샷부터 지워야 한다(FK 순서).
     */
    @Modifying
    @Query("delete from CeremonyPlanHistory h where h.ceremony.id = :ceremonyId")
    void deleteAllByCeremonyId(@Param("ceremonyId") Long ceremonyId);
}
