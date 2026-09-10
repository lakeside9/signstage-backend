package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventOptionalFeature;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyEventOptionalFeatureRepository extends JpaRepository<CeremonyEventOptionalFeature, Long> {

    List<CeremonyEventOptionalFeature> findAllByCeremonyEventId(Long ceremonyEventId);

    void deleteAllByCeremonyEventId(Long ceremonyEventId);

    /** 단위 상품 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 실제 하위 행사에 적용된 적이 있는지. */
    boolean existsByUnitProductId(Long unitProductId);
}
