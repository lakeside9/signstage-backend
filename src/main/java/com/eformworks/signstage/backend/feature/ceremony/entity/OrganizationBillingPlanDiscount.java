package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조직×과금 플랜 세밀 할인 오버라이드. signstage-docs
 * business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토: 조직×품목
 * 세밀 오버라이드 안 채택) 참고. 이 조직이 이 플랜을 쓸 때 카탈로그의 {@code discountType}/
 * {@code discountValue} 대신 여기 값을 쓴다. 이 조직×이 플랜 조합에 오버라이드 행이 없으면
 * 카탈로그 값을 그대로 쓴다({@code OrganizationDiscountService#resolveBillingPlanDiscount}).
 *
 * <p>이 값은 {@code Ceremony} 생성(플랜 최초 선택)/변경 시점에 {@link CeremonyPlanHistory}로
 * 스냅샷되므로, 나중에 여기 값을 바꿔도 이미 만들어진 Ceremony의 계산 결과에는 영향을 주지
 * 않는다(같은 문서 4.1절 결정 — "라이브 참조" 대신 "생성 시점 스냅샷 고정").
 */
@Entity
@Table(
        name = "organization_billing_plan_discounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_obpd_organization_plan",
                columnNames = {"organization_id", "billing_plan_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationBillingPlanDiscount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Builder
    private OrganizationBillingPlanDiscount(
            Organization organization,
            BillingPlan billingPlan,
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        this.organization = organization;
        this.billingPlan = billingPlan;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    /** 플랫폼 관리자가 이미 있는 오버라이드 값을 고칠 때 쓴다. */
    public void update(DiscountType discountType, BigDecimal discountValue) {
        this.discountType = discountType;
        this.discountValue = discountValue;
    }
}
