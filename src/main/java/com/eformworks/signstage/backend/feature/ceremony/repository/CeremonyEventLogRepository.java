package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.ActorType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyEventLogRepository extends JpaRepository<CeremonyEventLog, Long> {

    List<CeremonyEventLog> findAllByCeremonyEventId(Long ceremonyEventId);

    boolean existsByCeremonyEventIdAndActorTypeAndActorIdAndEventAction(
            Long ceremonyEventId,
            ActorType actorType,
            Long actorId,
            CeremonyEventAction eventAction
    );

    /**
     * {@code eventAction}이 {@code SIGNATURE_COMPLETE}/{@code SIGNATURE_REPLACE} 둘 중 어느
     * 쪽이든, 이 서명자({@code targetSignerId})에 대한 가장 최근 로그 한 건을 가져온다 —
     * "지금 완료 상태인가"는 이 최신 로그의 종류로 판정한다(append-only라 값을 고치는 대신
     * 최신 행위로 판정). {@code actorId}로 조회하지 않는 이유는 {@link CeremonyEventLog}의
     * 클래스 주석 참고 — SIGNATURE_REPLACE는 actor가 관리자라 signerId로 못 찾는다.
     */
    Optional<CeremonyEventLog> findTopByCeremonyEventIdAndTargetSignerIdAndEventActionInOrderByCreatedAtDesc(
            Long ceremonyEventId,
            Long targetSignerId,
            List<CeremonyEventAction> eventActions
    );
}
