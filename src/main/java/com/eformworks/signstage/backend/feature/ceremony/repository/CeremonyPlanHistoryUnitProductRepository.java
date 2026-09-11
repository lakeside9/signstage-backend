package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CeremonyPlanHistoryUnitProductRepository extends AppendOnlyRepository<CeremonyPlanHistoryUnitProduct, Long> {

    List<CeremonyPlanHistoryUnitProduct> findAllByCeremonyPlanHistoryId(Long ceremonyPlanHistoryId);

    /** 단위 상품 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 실제 행사에 스냅샷된 적이 있는지. */
    boolean existsByUnitProductId(Long unitProductId);

    /**
     * 플랜이 확정되지 않은(DRAFT) 행사 삭제 시 이 행사의 플랜 이력이 스냅샷해 둔 단위 상품 구성
     * 행을 함께 지운다 — {@code ceremony_id} 컬럼이 없어(부모는
     * {@link com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory}) 조인
     * 경로로 찾는다. {@code CeremonyPlanHistoryRepository.deleteAllByCeremonyId}보다 먼저
     * 호출해야 한다(FK 순서).
     */
    @Modifying
    @Query("delete from CeremonyPlanHistoryUnitProduct h where h.ceremonyPlanHistory.ceremony.id = :ceremonyId")
    void deleteAllByCeremonyPlanHistory_CeremonyId(@Param("ceremonyId") Long ceremonyId);
}
