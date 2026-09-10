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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * {@link UnitProductPricePeriod}(단위 상품 판매가격 기간)의 생성/수정/삭제 이력. append-only다 —
 * {@link BillingPlanPricePeriodHistory}와 같은 패턴.
 */
@Entity
@Table(name = "unit_product_price_period_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class UnitProductPricePeriodHistory extends BaseEntity {

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

    @Column(nullable = false)
    private boolean removed;

    @Builder
    private UnitProductPricePeriodHistory(UnitProduct unitProduct, UnitProductPricePeriod period, boolean removed) {
        this.unitProduct = unitProduct;
        this.priceInfo = period.getPriceInfo();
        this.active = period.isActive();
        this.effectiveFrom = period.getEffectiveFrom();
        this.effectiveTo = period.getEffectiveTo();
        this.removed = removed;
    }
}
