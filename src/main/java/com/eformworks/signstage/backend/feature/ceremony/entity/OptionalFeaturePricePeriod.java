package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link OptionalFeature}의 판매가격/사용여부 기간별 버전 — {@link BillingPlanPricePeriod}와
 * 같은 목적/패턴(signstage-docs business/billing-catalog-price-validity-period-review.md
 * 결정, 2026-09-09, 다중버전 채택). {@code OptionalFeature}는 이제 정체성(id/code/name/
 * exclusivityGroup/category)만 갖고, 가격정보와 사용여부는 전부 이 엔티티로 옮겨졌다.
 */
@Entity
@Table(
        name = "optional_feature_price_periods",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ofpp_feature_period",
                columnNames = {"optional_feature_id", "effective_from"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionalFeaturePricePeriod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Embedded
    private CatalogPriceInfo priceInfo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Builder
    private OptionalFeaturePricePeriod(
            OptionalFeature optionalFeature,
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            String taxCode,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.optionalFeature = optionalFeature;
        this.priceInfo = CatalogPriceInfo.of(
                currencyCode, supplyPrice, salePrice, discountType, discountValue,
                taxCode == null || taxCode.isBlank() ? "KR_VAT_STANDARD" : taxCode
        );
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public void update(
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            String taxCode,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.priceInfo = CatalogPriceInfo.of(
                currencyCode, supplyPrice, salePrice, discountType, discountValue,
                taxCode == null || taxCode.isBlank() ? this.priceInfo.getTaxCode() : taxCode
        );
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
