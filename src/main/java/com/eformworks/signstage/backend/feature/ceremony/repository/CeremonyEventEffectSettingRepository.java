package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSetting;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSettingId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CeremonyEventEffectSettingRepository
        extends JpaRepository<CeremonyEventEffectSetting, CeremonyEventEffectSettingId> {

    /** 행사 설정 화면·프로젝터 snapshot 조회용 fetch join — BE-CATALOG-01. */
    @Query(
            "select s from CeremonyEventEffectSetting s "
                    + "join fetch s.definition "
                    + "where s.id.eventId = :eventId "
                    + "order by s.id.targetType asc, s.id.triggerType asc"
    )
    List<CeremonyEventEffectSetting> findAllByEventIdWithDefinition(Long eventId);
}
