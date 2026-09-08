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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랜이 기본 포함하는 용량 한도(다대다 조인) — signstage-docs
 * business/billing-catalog-zero-base-schema-redesign-review.md 결정(2026-09-08, 항목 B).
 * 예전엔 서명자/템플릿/테스트행사/리허설행사/본행사 5종이 {@code BillingPlan}에 고정 컬럼으로
 * 박혀 있었는데(그리고 {@code TABLETS}는 대응 컬럼이 없어 플랜 기본 포함을 표현할 수 없었다),
 * 이제 이 조인 테이블 하나로 "이 플랜은 이 {@link CapacityType}을 몇 개 기본 포함한다"를 표현한다
 * — 새 용량 종류가 생겨도 스키마 변경 없이 이 테이블에 행만 추가하면 된다.
 *
 * <p>{@link CapacityType#isPlanIncludable()}가 {@code true}인 종류만 이 테이블에 들어갈 수
 * 있다({@code BillingPlanService}가 등록/수정 시 강제) — {@code TABLETS}처럼 "플랜 기본 포함
 * 없이 항상 0에서 시작해 추가구매로만 늘어나는" 종류는 여기 절대 행이 생기지 않는다. 플랜
 * 수정 시 이 구성을 통째로 교체한다(delete-all-then-recreate, {@code BillingPlanOptionalFeature}와
 * 같은 패턴).
 */
@Entity
@Table(
        name = "billing_plan_capacities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bpc_plan_type",
                columnNames = {"billing_plan_id", "capacity_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlanCapacity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_type", nullable = false, length = 20)
    private CapacityType capacityType;

    @Column(name = "included_amount", nullable = false)
    private Integer includedAmount;

    @Builder
    private BillingPlanCapacity(BillingPlan billingPlan, CapacityType capacityType, Integer includedAmount) {
        this.billingPlan = billingPlan;
        this.capacityType = capacityType;
        this.includedAmount = includedAmount;
    }
}
