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
 * 단위 상품 추가구매 요청 헤더 — 옛 {@code CeremonyCapacityPurchase}+
 * {@code CeremonyOptionalFeaturePurchase} 2종을 하나로 합친다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10, 3.4절).
 *
 * <p>한 번의 구매 요청이 여러 단위 상품 줄을 동시에 담을 수 있는 "장바구니형" 구조다 —
 * 실제 줄 데이터는 {@link CeremonyUnitProductPurchaseLine}이 갖고, 승인/반려는 항상 이
 * 헤더 단위로 한 번에 처리된다(줄마다 따로 승인하지 않는다). 옛 {@code CapacityAddOn}의
 * {@code secondaryCapacityType}(한 상품이 두 용량을 동시에 늘리는 "묶음")이 하던 역할을
 * 이제 "한 요청에 여러 줄을 담는다"는 방식으로 대체한다 — 몇 종류든 자연히 확장된다.
 *
 * <p>요청 즉시 PENDING으로 생기고, 플랫폼 관리자가 승인해야 각 줄의 수량이 유효 한도/구매
 * 집계에 반영된다. 옛 두 엔티티의 {@code purchased*} 스냅샷 원칙, 승인 큐, 감사 로그 등
 * 동작 원칙은 전부 그대로 유지된다.
 */
@Entity
@Table(name = "ceremony_unit_product_purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyUnitProductPurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** {@code platform_admin_audit_log.admin_user_id}와 같은 이유로 FK 없는 순수 행위자 참조다. */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * 취소 기록 — 승인/반려 기록({@code reviewedBy}/{@code reviewedAt}/{@code rejectionReason})과
     * 별도 컬럼으로 둔다(signstage-docs
     * business/ceremony-unit-product-purchase-cancellation-review.md 3.1절, 2026-09-12
     * 결정). 덮어쓰면 "누가 언제 승인했는지"(자가-체크아웃이면 {@code reviewedBy=null}로 그
     * 자체가 정보였다)라는 원래 감사 기록이 사라진다 — 승인/취소 둘 다 한 행에서 보존한다.
     */
    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Builder
    private CeremonyUnitProductPurchase(Ceremony ceremony) {
        this.ceremony = ceremony;
        this.status = PurchaseStatus.PENDING;
    }

    public void approve(Long reviewedBy) {
        this.status = PurchaseStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    /**
     * 자가-체크아웃 승인 — 시스템 사용료(ESSENTIAL/APPLICATION)만 담긴 구매는 관리자 승인
     * 없이 즉시 반영된다(signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 2·4장 결정, 2026-09-11).
     * {@link #approve}와 결과 상태는 같지만 {@code reviewedBy}를 null로 둔다 — "관리자가
     * 승인한 게 아니다"를 그 컬럼 자체로 구분한다.
     */
    public void autoApprove() {
        this.status = PurchaseStatus.APPROVED;
        this.reviewedBy = null;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Long reviewedBy, String rejectionReason) {
        this.status = PurchaseStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
    }

    /**
     * 이미 승인(APPROVED)된 구매를 관리자가 취소한다 — signstage-docs
     * business/ceremony-unit-product-purchase-cancellation-review.md 결정(2026-09-12).
     * 대상이 APPROVED인지는 호출부({@code CeremonyService#findApprovedUnitProductPurchaseOrThrow})가
     * 이미 확인했다고 전제한다.
     */
    public void cancel(Long cancelledBy, String cancellationReason) {
        this.status = PurchaseStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = cancellationReason;
    }
}
