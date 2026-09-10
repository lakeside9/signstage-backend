package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CeremonyEventRepository extends JpaRepository<CeremonyEvent, Long> {

    List<CeremonyEvent> findAllByCeremonyId(Long ceremonyId);

    /** 하위 행사 목록 조회가 쓴다 — 표시 순서(displayOrder) 오름차순, 동률은 id 오름차순. */
    List<CeremonyEvent> findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(Long ceremonyId);

    List<CeremonyEvent> findAllByCeremonyIdAndEventType(Long ceremonyId, CeremonyEventType eventType);

    /**
     * 데모 시나리오 목록(signstage-docs
     * business/demo-account-exhibition-signer-preview-review.md 5.4절) — 데모 조직 소속
     * STARTED 이벤트 전체를 최신순으로 자동 나열한다. 별도 큐레이션 플래그 없음(같은 절 결정).
     */
    List<CeremonyEvent> findAllByCeremony_Organization_IdAndStatusOrderByCreatedAtDesc(
            Long organizationId, CeremonyEventStatus status
    );

    /**
     * 체험형 데모 사이트(legacy 재사용) 관리자 화면의 "행사 선택" 후보 — 데모 조직 소속 이벤트
     * 전체(signstage-docs business/demo-account-exhibition-signer-preview-review.md 13장).
     */
    List<CeremonyEvent> findAllByCeremony_Organization_DemoTrueOrderByCreatedAtDesc();

    long countByCeremonyIdAndEventType(Long ceremonyId, CeremonyEventType eventType);

    long countByCeremonyId(Long ceremonyId);

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

    /**
     * 전원완료 자동 효과의 "최초 1회" 원자적 claim(BE-RUNTIME-02) — 조건부 UPDATE 자체가
     * compare-and-swap이라 별도 잠금이 필요 없다. 반환값이 1이면 이 호출이 claim에 성공한
     * 것이고(첫 실행 자격이 있다), 0이면 이미 다른 호출이 먼저 claim한 것이다(경합 패배 또는
     * 재완료). {@link CeremonyEffectRuntimeService#tryAutomaticCelebration}이 "전원 완료"를
     * 확인한 뒤 이 메서드를 호출해 실제 방송 여부를 최종 결정한다.
     */
    @Modifying
    @Query(
            "update CeremonyEvent e set e.autoCelebrationTriggeredAt = CURRENT_TIMESTAMP "
                    + "where e.id = :eventId and e.autoCelebrationTriggeredAt is null"
    )
    int claimAutomaticCelebration(@Param("eventId") Long eventId);
}
