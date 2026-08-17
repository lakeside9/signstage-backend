package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.StrokeData;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrokeDataRepository extends JpaRepository<StrokeData, Long> {

    List<StrokeData> findAllByCeremonyEventIdAndSignerIdAndTemplateFieldId(
            Long ceremonyEventId,
            Long signerId,
            Long templateFieldId
    );

    boolean existsByCeremonyEventIdAndSignerIdAndTemplateFieldId(
            Long ceremonyEventId,
            Long signerId,
            Long templateFieldId
    );
}
