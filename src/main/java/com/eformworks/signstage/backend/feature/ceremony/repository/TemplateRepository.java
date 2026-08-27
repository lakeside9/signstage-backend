package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    List<Template> findAllByCeremonyId(Long ceremonyId);

    /** 문서 양식 목록 조회가 쓴다 — 표시 순서(displayOrder) 오름차순, 동률은 id 오름차순. */
    List<Template> findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(Long ceremonyId);

    long countByCeremonyId(Long ceremonyId);
}
