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

    /**
     * 행사제어/프로젝터 화면이 늦게 들어와도 이미 그려진 획을 캐치업하려고 쓴다 — 필드별
     * 순서(strokeSeq)까지는 상관없고, 화면이 필드별로 다시 묶어 그린다.
     */
    List<StrokeData> findAllByCeremonyEventId(Long ceremonyEventId);

    /** SIGNATURE_CLEAR — 서명자 본인이 서명란 하나를 지우고 다시 그릴 때. */
    void deleteAllByCeremonyEventIdAndSignerIdAndTemplateFieldId(
            Long ceremonyEventId,
            Long signerId,
            Long templateFieldId
    );

    /** SIGNATURE_REPLACE — 관리자가 한 서명자의 이 이벤트 서명 진행 상황 전체를 초기화할 때. */
    void deleteAllByCeremonyEventIdAndSignerId(Long ceremonyEventId, Long signerId);

    /** 서명자 삭제 전 "실제로 서명한 기록이 있는지" 확인용 — 이벤트 구분 없이 전체를 본다. */
    boolean existsBySignerId(Long signerId);
}
