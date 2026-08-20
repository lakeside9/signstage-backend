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
 * 필수옵션(용량 한도) 추가구매 요청. 유효 한도 = 플랜의 기본값 + Σ(APPROVED인 것만, quantity ×
 * purchasedUnitAmount)(signstage-docs business/ceremony-billing-options-review.md 3장, 9장).
 * 요청 즉시 PENDING으로 생기고, 플랫폼 관리자가 승인해야 한도에 반영된다. {@code purchased*}
 * 필드는 구매 시점 스냅샷이다 — 카탈로그 가격/단가가 나중에 바뀌어도 이미 발생한 구매 내역은
 * 바뀌지 않아야 한다. {@code purchasedUnitAmount}는 9장에서 추가됐다(그 전에는 단가를
 * {@code capacityAddOn.getUnitAmount()}로 라이브 조회해 카탈로그 수정에 영향받는 결함이 있었다).
 */
@Entity
@Table(name = "ceremony_capacity_purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyCapacityPurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capacity_addon_id", nullable = false)
    private CapacityAddOn capacityAddOn;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "purchased_unit_amount", nullable = false)
    private Integer purchasedUnitAmount;

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
    private CeremonyCapacityPurchase(
            Ceremony ceremony,
            CapacityAddOn capacityAddOn,
            Integer quantity,
            Integer purchasedUnitAmount,
            BigDecimal purchasedSalePrice,
            DiscountType purchasedDiscountType,
            BigDecimal purchasedDiscountValue
    ) {
        this.ceremony = ceremony;
        this.capacityAddOn = capacityAddOn;
        this.quantity = quantity;
        this.purchasedUnitAmount = purchasedUnitAmount;
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
