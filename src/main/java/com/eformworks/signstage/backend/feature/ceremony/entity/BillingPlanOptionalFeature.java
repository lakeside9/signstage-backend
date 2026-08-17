package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Entity;
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
 * 플랜에 기본으로 포함되는 선택옵션 매핑(다대다 조인). signstage-docs
 * business/ceremony-billing-options-review.md 3장 {@code BillingPlanOptionalFeature} 참고.
 */
@Entity
@Table(
        name = "billing_plan_optional_features",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bpof_plan_feature",
                columnNames = {"billing_plan_id", "optional_feature_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlanOptionalFeature extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Builder
    private BillingPlanOptionalFeature(BillingPlan billingPlan, OptionalFeature optionalFeature) {
        this.billingPlan = billingPlan;
        this.optionalFeature = optionalFeature;
    }
}
