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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 필수옵션(용량 한도) 추가구매 내역. 유효 한도 = 플랜의 기본값 + Σ(quantity × addon.unitAmount)
 * (signstage-docs business/ceremony-billing-options-review.md 3장). {@code purchased*} 3개
 * 필드는 구매 시점 가격 스냅샷이다 — 카탈로그 가격이 나중에 바뀌어도 이미 발생한 구매 내역은
 * 바뀌지 않아야 한다.
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

    @Column(name = "purchased_sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasedSalePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchased_discount_type", nullable = false, length = 20)
    private DiscountType purchasedDiscountType;

    @Column(name = "purchased_discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasedDiscountValue;

    @Builder
    private CeremonyCapacityPurchase(
            Ceremony ceremony,
            CapacityAddOn capacityAddOn,
            Integer quantity,
            BigDecimal purchasedSalePrice,
            DiscountType purchasedDiscountType,
            BigDecimal purchasedDiscountValue
    ) {
        this.ceremony = ceremony;
        this.capacityAddOn = capacityAddOn;
        this.quantity = quantity;
        this.purchasedSalePrice = purchasedSalePrice;
        this.purchasedDiscountType = purchasedDiscountType;
        this.purchasedDiscountValue = purchasedDiscountValue;
    }
}
