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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 현장지원 요청(관리자 견적) 한 건 — {@code Ceremony} 직속(Signer/Template/CeremonyInquiry와
 * 같은 위치). 파트너가 일시·장소를 적어 요청하면, 관리자가 거리 등을 보고 실제 금액을
 * 매기고(정가 카탈로그가 아니라 그때그때 판단), 파트너가 그 금액을 수락/거부한다 —
 * signstage-docs business/onsite-support-negotiation-and-billing-classification-review.md
 * 3.2절 결정(2026-09-12).
 *
 * <p>수락(ACCEPTED)되면 {@code CeremonyOnsiteSupportRequestService}가 이 요청과 별개로
 * {@code CeremonyUnitProductPurchase}(+1줄)를 만든다 — {@code purchaseId}는 그 결과를
 * 추적하는 참조일 뿐, 실제 금액·구매 이력은 그 구매 엔티티가 갖는다(2.2절 발견 — 구매 원장은
 * 카테고리와 무관하게 전부 "플랫폼 이용료"로 집계되므로, 그 구매가 생기는 순간부터는 평범한
 * 추가구매와 똑같이 취급된다).
 */
@Entity
@Table(name = "ceremony_onsite_support_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyOnsiteSupportRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    /** 지원이 필요한 일시(파트너 입력) — 특정 하위 행사(CeremonyEvent)와 연동하지 않는다(v1). */
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(name = "requester_note", length = 500)
    private String requesterNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnsiteSupportRequestStatus status;

    @Column(name = "quoted_amount", precision = 19, scale = 4)
    private BigDecimal quotedAmount;

    @Column(name = "quoted_note", length = 500)
    private String quotedNote;

    /** 견적을 매긴 관리자 — {@code platform_admin_audit_log.admin_user_id}와 같은 이유로 FK 없는 순수 행위자 참조다. */
    @Column(name = "quoted_by")
    private Long quotedBy;

    @Column(name = "quoted_at")
    private LocalDateTime quotedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /** ACCEPTED 시 생성된 {@code CeremonyUnitProductPurchase.id} — 추적용, FK 아님(스냅샷 원칙과 같은 이유). */
    @Column(name = "purchase_id")
    private Long purchaseId;

    @Builder
    private CeremonyOnsiteSupportRequest(Ceremony ceremony, LocalDateTime requestedAt, String location, String requesterNote) {
        this.ceremony = ceremony;
        this.requestedAt = requestedAt;
        this.location = location;
        this.requesterNote = requesterNote;
        this.status = OnsiteSupportRequestStatus.REQUESTED;
    }

    /** 관리자가 거리 등을 보고 실제 금액을 매긴다 — REQUESTED에서만 호출된다고 전제한다(호출부가 미리 확인). */
    public void quote(BigDecimal quotedAmount, String quotedNote, Long quotedBy) {
        this.status = OnsiteSupportRequestStatus.QUOTED;
        this.quotedAmount = quotedAmount;
        this.quotedNote = quotedNote;
        this.quotedBy = quotedBy;
        this.quotedAt = LocalDateTime.now();
    }

    /** 파트너 수락 — QUOTED에서만 호출된다고 전제한다. 결과로 생긴 구매의 id를 남긴다. */
    public void accept(Long purchaseId) {
        this.status = OnsiteSupportRequestStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
        this.purchaseId = purchaseId;
    }

    /** 파트너 거부 — 종결, 재협상 없음(다시 필요하면 새 요청). QUOTED에서만 호출된다고 전제한다. */
    public void decline() {
        this.status = OnsiteSupportRequestStatus.DECLINED;
        this.respondedAt = LocalDateTime.now();
    }
}
