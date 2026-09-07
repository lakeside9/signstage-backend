package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.ActorType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import java.util.List;

public interface CeremonyEventLogRepository extends AppendOnlyRepository<CeremonyEventLog, Long> {

    List<CeremonyEventLog> findAllByCeremonyEventId(Long ceremonyEventId);

    boolean existsByCeremonyEventIdAndActorTypeAndActorIdAndEventAction(
            Long ceremonyEventId,
            ActorType actorType,
            Long actorId,
            CeremonyEventAction eventAction
    );

    /** 서명자 삭제 전 "감사 로그에 남아 있는지" 확인용 — 이벤트 구분 없이 전체를 본다. */
    boolean existsByTargetSignerId(Long targetSignerId);
}
