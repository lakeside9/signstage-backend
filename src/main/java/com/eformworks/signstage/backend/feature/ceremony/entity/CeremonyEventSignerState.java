package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사 이벤트 안에서 서명자 한 명의 "지금" 서명 상태를 투영해 둔 단일 기준 행 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-STATE. 예전에는 매번
 * {@code ceremony_event_logs}를 최신순으로 훑어 "가장 최근 로그가 SIGNATURE_COMPLETE인가"로
 * 판정했다({@code SignerPortalService}/{@code CeremonyEventService}에 각각 있던
 * {@code isSignerSignatureComplete}) — 이 행 하나만 보면 되도록 대체한다
 * ({@link CeremonyEventSignerStateService}).
 */
@Entity
@Table(name = "ceremony_event_signer_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEventSignerState extends BaseEntity {

    @EmbeddedId
    private CeremonyEventSignerStateId id;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private CeremonyEvent event;

    @MapsId("signerId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id")
    private Signer signer;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_status", nullable = false, length = 20)
    private SignatureStatus signatureStatus;

    @Column(name = "last_completion_log_id")
    private Long lastCompletionLogId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    private CeremonyEventSignerState(CeremonyEvent event, Signer signer) {
        this.event = event;
        this.signer = signer;
        this.id = new CeremonyEventSignerStateId(event.getId(), signer.getId());
        this.signatureStatus = SignatureStatus.PENDING;
    }

    /** SIGNATURE_COMPLETE — {@code completionLogId}는 그 감사 로그 id(추적용, PRE-04). */
    public void markCompleted(Long completionLogId) {
        this.signatureStatus = SignatureStatus.COMPLETED;
        this.lastCompletionLogId = completionLogId;
        this.completedAt = LocalDateTime.now();
    }

    /** SIGNATURE_REPLACE — 관리자가 이 서명자의 진행 상황을 초기화하고 "다시 서명하게" 한다. */
    public void markSigning() {
        this.signatureStatus = SignatureStatus.SIGNING;
        this.lastCompletionLogId = null;
        this.completedAt = null;
    }

    /** SIGNATURE_CLEAR — 서명자 본인이 자기 서명란 하나를 지운다. */
    public void markPending() {
        this.signatureStatus = SignatureStatus.PENDING;
        this.lastCompletionLogId = null;
        this.completedAt = null;
    }
}
