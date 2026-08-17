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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사 마스터(Ceremony) 단위 선택옵션 구매 내역(과금 단위). 실제로 어느 CeremonyEvent에
 * 켤지는 {@link CeremonyEventOptionalFeature}가 별도로 다룬다(signstage-docs
 * business/ceremony-billing-options-review.md 4.11절). {@code purchased*} 3개 필드는
 * 구매 시점 가격 스냅샷이다.
 */
@Entity
@Table(
        name = "ceremony_optional_feature_purchases",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cofp_ceremony_feature",
                columnNames = {"ceremony_id", "optional_feature_id"}
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
    }
}
