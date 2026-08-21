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
 * Ceremony의 플랜 변경 이력. append-only다 — 수정/삭제 메서드를 두지 않는다. Ceremony 생성 시
 * (최초 플랜 선택)와 {@code CeremonyService#changePlan} 호출 시(DRAFT 상태에서만 가능)마다
 * 한 행씩 쌓인다 — signstage-docs business/ceremony-plan-confirmation-review.md 3.4절.
 *
 * <p>"지금 유효한 플랜"은 여전히 {@link Ceremony#getBillingPlan()}이 가리킨다. 이 테이블은
 * "그동안 어떤 플랜을 거쳐왔는지"와 "그때 그 플랜의 이름/가격/한도가 뭐였는지"(카탈로그가
 * 나중에 바뀌어도 안 바뀌는 스냅샷)를 보여주는 이력 전용이다 — {@code누가/언제}는
 * {@link BaseEntity#getCreatedBy()}/{@link BaseEntity#getCreatedAt()}로 충분해 별도 컬럼을
 * 두지 않는다.
 */
@Entity
@Table(name = "ceremony_plan_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyPlanHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "plan_supply_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal planSupplyPrice;

    @Column(name = "plan_sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal planSalePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_discount_type", nullable = false, length = 20)
    private DiscountType planDiscountType;

    @Column(name = "plan_discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal planDiscountValue;

    @Column(name = "plan_max_signers", nullable = false)
    private Integer planMaxSigners;

    @Column(name = "plan_max_templates", nullable = false)
    private Integer planMaxTemplates;

    @Column(name = "plan_max_test_events", nullable = false)
    private Integer planMaxTestEvents;

    @Column(name = "plan_max_main_events", nullable = false)
    private Integer planMaxMainEvents;

    /**
     * {@code discountType}/{@code discountValue}는 보통 {@code billingPlan}에서 그대로 뽑지만,
     * 조직×플랜 오버라이드가 있으면({@link OrganizationBillingPlanDiscount},
     * {@code OrganizationDiscountService#resolveBillingPlanDiscount}) 호출부가 그 값을 대신
     * 넘긴다 — null이면(오버라이드 없음) 카탈로그 값으로 그대로 떨어진다.
     */
    @Builder
    private CeremonyPlanHistory(Ceremony ceremony, BillingPlan billingPlan, DiscountType discountType, BigDecimal discountValue) {
        this.ceremony = ceremony;
        this.billingPlan = billingPlan;
        this.planName = billingPlan.getName();
        this.planSupplyPrice = billingPlan.getSupplyPrice();
        this.planSalePrice = billingPlan.getSalePrice();
        this.planDiscountType = discountType != null ? discountType : billingPlan.getDiscountType();
        this.planDiscountValue = discountValue != null ? discountValue : billingPlan.getDiscountValue();
        this.planMaxSigners = billingPlan.getMaxSigners();
        this.planMaxTemplates = billingPlan.getMaxTemplates();
        this.planMaxTestEvents = billingPlan.getMaxTestEvents();
        this.planMaxMainEvents = billingPlan.getMaxMainEvents();
    }
}
