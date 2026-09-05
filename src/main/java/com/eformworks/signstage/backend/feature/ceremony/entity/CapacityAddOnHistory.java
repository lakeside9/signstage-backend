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
import org.hibernate.annotations.Immutable;

/**
 * 용량 추가구매 상품(CapacityAddOn)의 값/사용여부 변경 이력. append-only다 — {@link BillingPlanHistory}와
 * 같은 패턴. {@code capacityType}은 원본에서 불변이지만 조인 없이 이력만으로 표시할 수 있게
 * 그대로 스냅샷에 포함한다.
 */
@Entity
@Table(name = "capacity_addon_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class CapacityAddOnHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capacity_addon_id", nullable = false)
    private CapacityAddOn capacityAddOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_type", nullable = false, length = 20)
    private CapacityType capacityType;

    @Column(name = "unit_amount", nullable = false)
    private Integer unitAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_capacity_type", length = 20)
    private CapacityType secondaryCapacityType;

    @Column(name = "secondary_unit_amount")
    private Integer secondaryUnitAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "supply_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private CapacityAddOnHistory(CapacityAddOn capacityAddOn) {
        this.capacityAddOn = capacityAddOn;
        this.capacityType = capacityAddOn.getCapacityType();
        this.unitAmount = capacityAddOn.getUnitAmount();
        this.secondaryCapacityType = capacityAddOn.getSecondaryCapacityType();
        this.secondaryUnitAmount = capacityAddOn.getSecondaryUnitAmount();
        this.currencyCode = capacityAddOn.getCurrencyCode();
        this.supplyPrice = capacityAddOn.getSupplyPrice();
        this.salePrice = capacityAddOn.getSalePrice();
        this.discountType = capacityAddOn.getDiscountType();
        this.discountValue = capacityAddOn.getDiscountValue();
        this.taxCode = capacityAddOn.getTaxCode();
        this.active = capacityAddOn.isActive();
    }
}
