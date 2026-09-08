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
 * {@link BillingPlanHistory} 스냅샷 시점의 {@link BillingPlanCapacity} 구성 — append-only다.
 * {@code BillingPlanService#recordPlanHistory}가 생성/수정 시점마다 그 순간의
 * {@code BillingPlanCapacity} 행 전체를 복사해 저장한다({@link CeremonyPlanHistoryCapacityAddOn}과
 * 같은 패턴).
 */
@Entity
@Table(name = "billing_plan_history_capacities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class BillingPlanHistoryCapacity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_history_id", nullable = false)
    private BillingPlanHistory billingPlanHistory;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_type", nullable = false, length = 20)
    private CapacityType capacityType;

    @Column(name = "included_amount", nullable = false)
    private Integer includedAmount;

    @Builder
    private BillingPlanHistoryCapacity(BillingPlanHistory billingPlanHistory, CapacityType capacityType, Integer includedAmount) {
        this.billingPlanHistory = billingPlanHistory;
        this.capacityType = capacityType;
        this.includedAmount = includedAmount;
    }
}
