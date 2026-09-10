package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import java.util.List;

public interface CeremonyPlanHistoryUnitProductRepository extends AppendOnlyRepository<CeremonyPlanHistoryUnitProduct, Long> {

    List<CeremonyPlanHistoryUnitProduct> findAllByCeremonyPlanHistoryId(Long ceremonyPlanHistoryId);
}
