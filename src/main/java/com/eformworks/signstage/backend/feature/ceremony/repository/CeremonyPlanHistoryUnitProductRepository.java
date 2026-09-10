package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import java.util.List;

public interface CeremonyPlanHistoryUnitProductRepository extends AppendOnlyRepository<CeremonyPlanHistoryUnitProduct, Long> {

    List<CeremonyPlanHistoryUnitProduct> findAllByCeremonyPlanHistoryId(Long ceremonyPlanHistoryId);

    /** 단위 상품 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 실제 행사에 스냅샷된 적이 있는지. */
    boolean existsByUnitProductId(Long unitProductId);
}
