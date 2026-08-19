package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyTemplate;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyTemplateRepository extends JpaRepository<CeremonyTemplate, Long> {

    List<CeremonyTemplate> findAllByCeremonyEventId(Long ceremonyEventId);

    List<CeremonyTemplate> findAllByCeremonyEventIdAndDocumentRole(Long ceremonyEventId, TemplateDocumentRole documentRole);

    boolean existsByCeremonyEventIdAndTemplateId(Long ceremonyEventId, Long templateId);

    /** 문서 양식 삭제 전 "이미 하위 행사에 매핑됐는지" 확인용. */
    boolean existsByTemplateId(Long templateId);

    /** 문서 양식 수정 전 "시작/종료된 하위 행사에 매핑됐는지" 확인용 — 매핑된 이벤트 상태를 봐야 해서 전체를 가져온다. */
    List<CeremonyTemplate> findAllByTemplateId(Long templateId);

    /** 하위 행사 삭제 시 먼저 지운다 — CeremonyEvent 삭제 전 FK 정리. */
    void deleteAllByCeremonyEventId(Long ceremonyEventId);
}
