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
 * 과금 플랜(BillingPlan)의 값/사용여부 변경 이력. append-only다 — 수정/삭제 메서드를 두지
 * 않는다. 생성 시점과 {@code BillingPlanService#updatePlan} 호출 시(값 또는 active가 바뀔 때)
 * 마다 그 순간의 전체 상태를 스냅샷 한 행씩 쌓는다({@link CeremonyPlanHistory}와 같은 패턴).
 * "누가/언제"는 {@link BaseEntity#getCreatedBy()}/{@link BaseEntity#getCreatedAt()}로
 * 충분해 별도 컬럼을 두지 않는다.
 */
@Entity
@Table(name = "billing_plan_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlanHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "supply_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_signers", nullable = false)
    private Integer maxSigners;

    @Column(name = "max_templates", nullable = false)
    private Integer maxTemplates;

    @Column(name = "max_test_events", nullable = false)
    private Integer maxTestEvents;

    @Column(name = "max_rehearsal_events", nullable = false)
    private Integer maxRehearsalEvents;

    @Column(name = "max_main_events", nullable = false)
    private Integer maxMainEvents;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private BillingPlanHistory(BillingPlan billingPlan) {
        this.billingPlan = billingPlan;
        this.name = billingPlan.getName();
        this.supplyPrice = billingPlan.getSupplyPrice();
        this.salePrice = billingPlan.getSalePrice();
        this.discountType = billingPlan.getDiscountType();
        this.discountValue = billingPlan.getDiscountValue();
        this.maxSigners = billingPlan.getMaxSigners();
        this.maxTemplates = billingPlan.getMaxTemplates();
        this.maxTestEvents = billingPlan.getMaxTestEvents();
        this.maxRehearsalEvents = billingPlan.getMaxRehearsalEvents();
        this.maxMainEvents = billingPlan.getMaxMainEvents();
        this.active = billingPlan.isActive();
    }
}
