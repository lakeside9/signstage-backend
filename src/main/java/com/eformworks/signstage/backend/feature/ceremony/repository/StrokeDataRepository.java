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

    /** 결과 PDF 렌더링용 — 이 서명란에 남겨진 모든 획을 그린 순서(strokeSeq)대로 가져온다. */
    List<StrokeData> findAllByCeremonyEventIdAndTemplateFieldIdOrderByStrokeSeq(
            Long ceremonyEventId,
            Long templateFieldId
    );
}
