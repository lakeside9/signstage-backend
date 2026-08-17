package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateFieldRepository extends JpaRepository<TemplateField, Long> {

    List<TemplateField> findAllByTemplateId(Long templateId);
}
