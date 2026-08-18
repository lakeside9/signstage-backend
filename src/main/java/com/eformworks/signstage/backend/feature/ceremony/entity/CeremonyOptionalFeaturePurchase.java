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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사 마스터(Ceremony) 단위 선택옵션 구매 요청(과금 단위). 실제로 어느 CeremonyEvent에 켤지는
 * {@link CeremonyEventOptionalFeature}가 별도로 다룬다(signstage-docs
 * business/ceremony-billing-options-review.md 4.11절). 요청 즉시 PENDING으로 생기고, 플랫폼
 * 관리자가 승인해야 "구매한 선택옵션" 집계에 반영된다. {@code purchased*} 3개 필드는 구매
 * 시점 가격 스냅샷이다.
 *
 * <p>유니크 제약이 status까지 포함한다 — 반려(REJECTED)된 요청은 그 조합을 계속 차지하면
 * 재요청이 막히므로, PENDING/APPROVED/REJECTED 상태별로 별도 행을 허용한다. "이미 구매(요청)한
 * 옵션"인지는 status를 걸러 서비스 레이어에서 판정한다(PENDING/APPROVED가 있으면 막고,
 * REJECTED만 있으면 재요청을 허용).
 */
@Entity
@Table(
        name = "ceremony_optional_feature_purchases",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cofp_ceremony_feature_status",
                columnNames = {"ceremony_id", "optional_feature_id", "status"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyOptionalFeaturePurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Column(name = "purchased_sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasedSalePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchased_discount_type", nullable = false, length = 20)
    private DiscountType purchasedDiscountType;

    @Column(name = "purchased_discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasedDiscountValue;

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

    @Builder
    private CeremonyOptionalFeaturePurchase(
            Ceremony ceremony,
            OptionalFeature optionalFeature,
            BigDecimal purchasedSalePrice,
            DiscountType purchasedDiscountType,
            BigDecimal purchasedDiscountValue
    ) {
        this.ceremony = ceremony;
        this.optionalFeature = optionalFeature;
        this.purchasedSalePrice = purchasedSalePrice;
        this.purchasedDiscountType = purchasedDiscountType;
        this.purchasedDiscountValue = purchasedDiscountValue;
        this.status = PurchaseStatus.PENDING;
    }

    public void approve(Long reviewedBy) {
        this.status = PurchaseStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Long reviewedBy, String rejectionReason) {
        this.status = PurchaseStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
    }
}
