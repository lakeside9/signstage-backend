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
 * 조직×선택옵션 세밀 할인 오버라이드. {@link OrganizationBillingPlanDiscount}와 같은 목적·같은
 * 스냅샷 원칙이다 — 이 조직이 이 선택옵션을 구매할 때({@code CeremonyService#purchaseOptionalFeature})
 * 그 순간 {@code CeremonyOptionalFeaturePurchase.purchasedDiscountType/Value}로 스냅샷되므로,
 * 나중에 여기 값을 바꿔도 이미 구매된 건에는 영향을 주지 않는다.
 */
@Entity
@Table(
        name = "organization_optional_feature_discounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_oofd_organization_feature",
                columnNames = {"organization_id", "optional_feature_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationOptionalFeatureDiscount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Builder
    private OrganizationOptionalFeatureDiscount(
            Organization organization,
            OptionalFeature optionalFeature,
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        this.organization = organization;
        this.optionalFeature = optionalFeature;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    public void update(DiscountType discountType, BigDecimal discountValue) {
        this.discountType = discountType;
        this.discountValue = discountValue;
    }
}
