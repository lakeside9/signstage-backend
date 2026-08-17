package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOptionalFeaturePurchase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyOptionalFeaturePurchaseRepository extends JpaRepository<CeremonyOptionalFeaturePurchase, Long> {

    List<CeremonyOptionalFeaturePurchase> findAllByCeremonyId(Long ceremonyId);

    boolean existsByCeremonyIdAndOptionalFeatureId(Long ceremonyId, Long optionalFeatureId);
}
