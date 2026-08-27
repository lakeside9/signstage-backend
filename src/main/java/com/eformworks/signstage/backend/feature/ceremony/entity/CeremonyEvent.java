package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실제 라이프사이클을 갖는 하위 행사(TEST/MAIN). 이번 라운드는 DRAFT 생성까지만 다루므로
 * 상태 변경 메서드는 아직 두지 않는다 — READY/START/FINISH 전이는 Template/Signer 검증이
 * 필요한 다음 라운드에 추가한다(signstage-docs business/ceremony-feature-migration-review.md
 * 2.2절).
 */
@Entity
@Table(name = "ceremony_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private CeremonyEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CeremonyEventStatus status;

    @Column(length = 200)
    private String venue;

    @Column(name = "scheduled_start_at")
    private LocalDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at")
    private LocalDateTime scheduledEndAt;

    @Column(name = "actual_start_at")
    private LocalDateTime actualStartAt;

    @Column(name = "actual_end_at")
    private LocalDateTime actualEndAt;

    @Column(name = "access_key", nullable = false, unique = true, length = 64)
    private String accessKey;

    @Column(length = 1000)
    private String description;

    /**
     * 행사 상세 목록 화면의 표시 순서(2026-08-27 legacy 포팅) — 위/아래 이동 버튼이 전체 목록을
     * 다시 인덱싱해 저장한다({@code CeremonyEventService#updateEventDisplayOrders}). TEST/
     * REHEARSAL/MAIN 구분과 무관하게 이 Ceremony의 하위 행사 전체가 하나의 순서를 공유한다 —
     * legacy처럼 구분별 탭으로 나눠 따로 정렬하지 않는다(화면이 이미 탭 없이 한 표에 전부
     * 보여주는 구조라 굳이 나누지 않기로 한 판단).
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Builder
    private CeremonyEvent(
            Ceremony ceremony,
            String name,
            CeremonyEventType eventType,
            String venue,
            LocalDateTime scheduledStartAt,
            LocalDateTime scheduledEndAt,
            String accessKey,
            String description,
            Integer displayOrder
    ) {
        this.ceremony = ceremony;
        this.name = name;
        this.eventType = eventType;
        this.status = CeremonyEventStatus.DRAFT;
        this.venue = venue;
        this.scheduledStartAt = scheduledStartAt;
        this.scheduledEndAt = scheduledEndAt;
        this.accessKey = accessKey;
        this.description = description;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    /**
     * 하위 행사 수정 화면에서 기본 정보를 바꿀 때 쓴다. 구분(eventType)은 한도 계산과 얽혀 있어
     * 여기서 바꾸지 않는다(생성 시점에 고정).
     */
    public void updateInfo(String name, String venue, LocalDateTime scheduledStartAt, LocalDateTime scheduledEndAt, String description) {
        this.name = name;
        this.venue = venue;
        this.scheduledStartAt = scheduledStartAt;
        this.scheduledEndAt = scheduledEndAt;
        this.description = description;
    }

    /** 조건 검증은 서비스(CeremonyEventService)가 하고, 이 메서드는 상태만 바꾼다. */
    public void transitionToReady() {
        this.status = CeremonyEventStatus.READY;
    }

    public void transitionToStarted() {
        this.status = CeremonyEventStatus.STARTED;
        this.actualStartAt = LocalDateTime.now();
    }

    public void transitionToFinished() {
        this.status = CeremonyEventStatus.FINISHED;
        this.actualEndAt = LocalDateTime.now();
    }

    /**
     * STARTED에서 서명 완료 여부와 무관하게 강제로 끝낸다 — 조건 검증은 서비스
     * ({@code CeremonyEventService#forceFinishEvent})가 하고, 이 메서드는 상태만 바꾼다.
     */
    public void forceFinish() {
        this.status = CeremonyEventStatus.FORCE_FINISHED;
        this.actualEndAt = LocalDateTime.now();
    }

    /** 행사 상세 목록의 위/아래 이동 버튼이 호출한다 — {@code null}이면 바꾸지 않는다. */
    public void updateDisplayOrder(Integer displayOrder) {
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }
}
