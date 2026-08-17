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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하위 행사 append-only 감사 로그. 서명 완료 판정("이 서명자가 이 이벤트에서
 * SIGNATURE_COMPLETE 로그를 가졌는가")의 유일한 근거다. 변경 메서드가 없다 — 한 번 쓰면
 * 끝이다. {@code actorId}는 FK가 아니라 순수 컬럼이다 — {@code actorType}에 따라
 * {@code User.id}(ADMIN) 또는 {@code Signer.id}(SIGNER) 중 하나를 가리키는 다형적
 * 참조라 단일 FK로 표현할 수 없다.
 */
@Entity
@Table(name = "ceremony_event_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEventLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_event_id", nullable = false)
    private CeremonyEvent ceremonyEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_action", nullable = false, length = 30)
    private CeremonyEventAction eventAction;

    @Column(length = 1000)
    private String message;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // @Lob를 안 쓴다 — StrokeData.rawData와 같은 이유(TINYTEXT 검증 실패 회피).
    @Column(name = "action_detail", columnDefinition = "TEXT")
    private String actionDetail;

    @Builder
    private CeremonyEventLog(
            CeremonyEvent ceremonyEvent,
            ActorType actorType,
            Long actorId,
            CeremonyEventAction eventAction,
            String message,
            String ipAddress,
            String userAgent,
            String actionDetail
    ) {
        this.ceremonyEvent = ceremonyEvent;
        this.actorType = actorType;
        this.actorId = actorId;
        this.eventAction = eventAction;
        this.message = message;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.actionDetail = actionDetail;
    }
}
