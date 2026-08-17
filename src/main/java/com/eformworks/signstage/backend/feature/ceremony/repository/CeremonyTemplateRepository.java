package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyTemplate;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyTemplateRepository extends JpaRepository<CeremonyTemplate, Long> {

    List<CeremonyTemplate> findAllByCeremonyEventId(Long ceremonyEventId);

    List<CeremonyTemplate> findAllByCeremonyEventIdAndDocumentRole(Long ceremonyEventId, TemplateDocumentRole documentRole);

    boolean existsByCeremonyEventIdAndTemplateId(Long ceremonyEventId, Long templateId);
}
