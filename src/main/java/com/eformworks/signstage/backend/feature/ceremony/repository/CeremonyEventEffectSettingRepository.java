package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSetting;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSettingId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 전원완료 자동/수동 실행, runtime ON/OFF(BE-RUNTIME)가 특정 분류 하나만 조회할 때 쓴다. */
    @Query(
            "select s from CeremonyEventEffectSetting s "
                    + "join fetch s.definition "
                    + "where s.id.eventId = :eventId and s.id.targetType = :targetType and s.id.triggerType = :triggerType"
    )
    Optional<CeremonyEventEffectSetting> findByEventIdAndClassificationWithDefinition(
            Long eventId, CeremonyEffectTarget targetType, CeremonyEffectTrigger triggerType
    );

    /**
     * BE-SETTING-01 — 선택 전체 교체(delete-all-then-recreate, {@code applyOptionalFeatures}와
     * 같은 패턴)의 삭제 단계. {@code @EmbeddedId} 하위 속성 경로라 derived delete 대신 명시적
     * JPQL을 쓴다.
     */
    @Modifying
    @Query("delete from CeremonyEventEffectSetting s where s.id.eventId = :eventId")
    void deleteAllByEventId(Long eventId);
}
