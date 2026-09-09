package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnPricePeriodHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapacityAddOnPricePeriodHistoryRepository extends JpaRepository<CapacityAddOnPricePeriodHistory, Long> {

    List<CapacityAddOnPricePeriodHistory> findAllByCapacityAddOnIdOrderByCreatedAtDesc(Long capacityAddOnId);
}
