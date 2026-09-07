package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CeremonyEffectDefinitionRepositoryCustom {

    /** FE-ADMIN-02(관리 화면 목록)를 위한 검색 — keyword는 code/displayName 부분일치(대소문자 무시). */
    Page<CeremonyEffectDefinition> search(
            String keyword,
            CeremonyEffectTarget targetType,
            CeremonyEffectTrigger triggerType,
            Boolean enabled,
            Boolean userVisible,
            Pageable pageable
    );
}
