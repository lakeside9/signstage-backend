package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriodHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitProductPricePeriodHistoryRepository extends JpaRepository<UnitProductPricePeriodHistory, Long> {

    List<UnitProductPricePeriodHistory> findAllByUnitProductIdOrderByCreatedAtDesc(Long unitProductId);
}
