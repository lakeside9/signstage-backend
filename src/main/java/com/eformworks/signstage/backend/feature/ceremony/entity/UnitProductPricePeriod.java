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
 * {@link UnitProduct}의 판매가격/사용여부 기간별 버전 — 기존 {@code OptionalFeaturePricePeriod}/
 * {@code CapacityAddOnPricePeriod} 통합. {@code UnitProduct}는 정체성(type/name/category/
 * exclusivityGroup)만 갖고, 가격정보와 사용여부는 전부 이 엔티티로 옮겨졌다. {@link
 * BillingPlanPricePeriod}와 달리 할인이 없다({@link ProductPriceInfo} 참고, 2026-09-10 결정).
 */
@Entity
@Table(
        name = "unit_product_price_periods",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_upp_product_period",
                columnNames = {"unit_product_id", "effective_from"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitProductPricePeriod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    @Embedded
    private ProductPriceInfo priceInfo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Builder
    private UnitProductPricePeriod(
            UnitProduct unitProduct,
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            String taxCode,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.unitProduct = unitProduct;
        this.priceInfo = ProductPriceInfo.of(
                currencyCode, supplyPrice, salePrice,
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
            String taxCode,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.priceInfo = ProductPriceInfo.of(
                currencyCode, supplyPrice, salePrice,
                taxCode == null || taxCode.isBlank() ? this.priceInfo.getTaxCode() : taxCode
        );
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
