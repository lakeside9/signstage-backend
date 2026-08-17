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

    /** SIGNATURE_CLEAR — 서명자 본인이 서명란 하나를 지우고 다시 그릴 때. */
    void deleteAllByCeremonyEventIdAndSignerIdAndTemplateFieldId(
            Long ceremonyEventId,
            Long signerId,
            Long templateFieldId
    );

    /** SIGNATURE_REPLACE — 관리자가 한 서명자의 이 이벤트 서명 진행 상황 전체를 초기화할 때. */
    void deleteAllByCeremonyEventIdAndSignerId(Long ceremonyEventId, Long signerId);
}
