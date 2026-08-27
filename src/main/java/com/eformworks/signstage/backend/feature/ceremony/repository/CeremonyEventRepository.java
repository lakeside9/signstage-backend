package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CeremonyEventRepository extends JpaRepository<CeremonyEvent, Long> {

    List<CeremonyEvent> findAllByCeremonyId(Long ceremonyId);

    /** 하위 행사 목록 조회가 쓴다 — 표시 순서(displayOrder) 오름차순, 동률은 id 오름차순. */
    List<CeremonyEvent> findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(Long ceremonyId);

    List<CeremonyEvent> findAllByCeremonyIdAndEventType(Long ceremonyId, CeremonyEventType eventType);

    long countByCeremonyIdAndEventType(Long ceremonyId, CeremonyEventType eventType);

    long countByCeremonyId(Long ceremonyId);

    /**
     * REHEARSAL이 TEST와 같은 용량 한도 버킷(CapacityType.TEST_EVENTS)을 공유하므로,
     * 한도 계산이 두 구분을 함께 센다({@code CeremonyEventService#createCeremonyEvent}).
     */
    long countByCeremonyIdAndEventTypeIn(Long ceremonyId, Collection<CeremonyEventType> eventTypes);

    boolean existsByAccessKey(String accessKey);

    Optional<CeremonyEvent> findByAccessKey(String accessKey);

    /**
     * 행 잠금(SELECT ... FOR UPDATE)으로 조회 — {@code SignerPortalService.completeSignature}가
     * "이 이벤트의 필수 서명자 전원이 방금 완료로 전환됐는가"를 판정하기 전에 이 잠금을 먼저
     * 잡아서, 같은 이벤트에 대한 동시 완료 요청들이 판정 구간에서 직렬화되게 한다(폭죽 중복/누락
     * 방지, signstage-docs business/ceremony-feature-migration-review.md 8.6/8.8절 참고).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CeremonyEvent e where e.id = :id")
    Optional<CeremonyEvent> findByIdForUpdate(@Param("id") Long id);
}
