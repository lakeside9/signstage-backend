package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyCapacityPurchase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyCapacityPurchaseRepository extends JpaRepository<CeremonyCapacityPurchase, Long> {

    List<CeremonyCapacityPurchase> findAllByCeremonyId(Long ceremonyId);

    List<CeremonyCapacityPurchase> findAllByCeremonyIdAndCapacityAddOn_CapacityType(
            Long ceremonyId,
            CapacityType capacityType
    );
}
