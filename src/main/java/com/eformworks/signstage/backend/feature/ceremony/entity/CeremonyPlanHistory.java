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
 * Ceremony의 플랜 변경 이력. append-only다 — 수정/삭제 메서드를 두지 않는다. Ceremony 생성 시
 * (최초 플랜 선택)와 {@code CeremonyService#changePlan} 호출 시(DRAFT 상태에서만 가능)마다
 * 한 행씩 쌓인다 — signstage-docs business/ceremony-plan-confirmation-review.md 3.4절.
 *
 * <p>"지금 유효한 플랜"은 여전히 {@link Ceremony#getBillingPlan()}이 가리킨다. 이 테이블은
 * "그동안 어떤 플랜을 거쳐왔는지"와 "그때 그 플랜의 이름/할인이 뭐였는지"(카탈로그가 나중에
 * 바뀌어도 안 바뀌는 스냅샷)를 보여주는 이력 전용이다. 플랜이 포함하던 단위 상품 구성(수량 ×
 * 그 순간 단가)은 이 엔티티의 고정 필드가 아니라 {@link CeremonyPlanHistoryUnitProduct}로 별도
 * 스냅샷된다.
 *
 * <p>플랜은 이제 자기 가격을 갖지 않으므로(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10) 옛
 * {@code planSupplyPrice}/{@code planSalePrice}/{@code taxCode} 필드가 없다 — "그때 그 플랜의
 * 가격"은 이제 {@link CeremonyPlanHistoryUnitProduct} 스냅샷들로부터
 * {@code CeremonyService#calculateEstimatedTotal}이 다시 계산한다. 이 엔티티엔 그 계산에
 * 적용할 플랜 자체 할인(카탈로그 기본값 또는 조직×플랜 오버라이드)만 남는다.
 */
@Entity
@Table(name = "ceremony_plan_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
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

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_discount_type", nullable = false, length = 20)
    private DiscountType planDiscountType;

    @Column(name = "plan_discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal planDiscountValue;

    /**
     * 카탈로그 기본 할인({@code catalogDiscountType}/{@code catalogDiscountValue})은 호출부
     * ({@code CeremonyService#recordPlanHistory})가 그 순간 유효한
     * {@link BillingPlanDiscountPeriod}를 {@code findEffective}로 조회해 넘긴다 — 이 엔티티는
     * DB 조회를 하지 않는다. 조직×플랜 오버라이드가 있으면({@link OrganizationBillingPlanDiscount},
     * {@code OrganizationDiscountService#resolveBillingPlanDiscount}) {@code discountType}/
     * {@code discountValue}에 그 값을 대신 넘긴다 — null이면(오버라이드 없음) 카탈로그 값으로
     * 그대로 떨어진다.
     */
    @Builder
    private CeremonyPlanHistory(
            Ceremony ceremony,
            BillingPlan billingPlan,
            DiscountType catalogDiscountType,
            BigDecimal catalogDiscountValue,
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        this.ceremony = ceremony;
        this.billingPlan = billingPlan;
        this.planName = billingPlan.getName();
        this.planDiscountType = discountType != null ? discountType : catalogDiscountType;
        this.planDiscountValue = discountValue != null ? discountValue : catalogDiscountValue;
    }
}
