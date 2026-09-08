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
 * {@link CeremonyPlanHistory} 스냅샷 시점에 그 플랜이 기본 포함하던 용량 한도 — append-only다.
 * Ceremony 생성(최초 플랜 선택)/{@code CeremonyService#changePlan} 시점마다 그 순간의
 * {@link BillingPlanCapacity} 구성을 그대로 복사한다({@link CeremonyPlanHistoryCapacityAddOn}과
 * 같은 패턴). 예전에 {@code CeremonyPlanHistory}가 갖던 {@code planMaxSigners} 등 고정 필드
 * 5개를 대체한다 — signstage-docs
 * business/billing-catalog-zero-base-schema-redesign-review.md 결정(2026-09-08, 항목 B).
 * {@code CeremonyService#calculateEffectiveCapacity}가 특정 {@link CapacityType}에 해당하는
 * 행이 없으면 0으로 취급한다(예: {@code TABLETS} — 플랜 기본 포함 없음).
 */
@Entity
@Table(name = "ceremony_plan_history_capacities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class CeremonyPlanHistoryCapacity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_plan_history_id", nullable = false)
    private CeremonyPlanHistory ceremonyPlanHistory;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_type", nullable = false, length = 20)
    private CapacityType capacityType;

    @Column(name = "included_amount", nullable = false)
    private Integer includedAmount;

    @Builder
    private CeremonyPlanHistoryCapacity(CeremonyPlanHistory ceremonyPlanHistory, CapacityType capacityType, Integer includedAmount) {
        this.ceremonyPlanHistory = ceremonyPlanHistory;
        this.capacityType = capacityType;
        this.includedAmount = includedAmount;
    }
}
