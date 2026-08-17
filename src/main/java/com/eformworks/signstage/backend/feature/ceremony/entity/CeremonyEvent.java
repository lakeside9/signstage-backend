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

    @Builder
    private CeremonyEvent(
            Ceremony ceremony,
            String name,
            CeremonyEventType eventType,
            String venue,
            LocalDateTime scheduledStartAt,
            LocalDateTime scheduledEndAt,
            String accessKey,
            String description
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
    }
}
