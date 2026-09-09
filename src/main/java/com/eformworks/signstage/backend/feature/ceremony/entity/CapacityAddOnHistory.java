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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 용량 추가구매 상품(CapacityAddOn)의 단위수량 변경 이력. append-only다 — {@link BillingPlanHistory}와
 * 같은 패턴. {@code capacityType}은 원본에서 불변이지만 조인 없이 이력만으로 표시할 수 있게
 * 그대로 스냅샷에 포함한다. 가격정보/사용여부 변경 이력은 {@link CapacityAddOnPricePeriodHistory}가
 * 담당한다(signstage-docs business/billing-catalog-price-validity-period-review.md 결정,
 * 2026-09-09) — {@link BillingPlanHistory}와 같은 이유로 축이 분리됐다.
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

    @Builder
    private CapacityAddOnHistory(CapacityAddOn capacityAddOn) {
        this.capacityAddOn = capacityAddOn;
        this.capacityType = capacityAddOn.getCapacityType();
        this.unitAmount = capacityAddOn.getUnitAmount();
        this.secondaryCapacityType = capacityAddOn.getSecondaryCapacityType();
        this.secondaryUnitAmount = capacityAddOn.getSecondaryUnitAmount();
    }
}
