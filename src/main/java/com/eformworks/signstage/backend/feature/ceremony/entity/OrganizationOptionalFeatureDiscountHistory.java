package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 조직×선택옵션 할인 오버라이드({@link OrganizationOptionalFeatureDiscount})의 변경 이력.
 * {@link OrganizationBillingPlanDiscountHistory}와 같은 패턴·같은 이유(오버라이드 하드 삭제
 * 대응)다.
 */
@Entity
@Table(name = "organization_optional_feature_discount_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class OrganizationOptionalFeatureDiscountHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Embedded
    @AttributeOverride(name = "discountValue", column = @Column(name = "discount_value", nullable = false, precision = 12, scale = 2))
    private DiscountInfo discount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** true면 이 행이 "오버라이드 제거" 이벤트다 — discount/effectiveFrom/effectiveTo는 제거 직전 값. */
    @Column(nullable = false)
    private boolean removed;

    @Builder
    private OrganizationOptionalFeatureDiscountHistory(
            Organization organization,
            OptionalFeature optionalFeature,
            DiscountType discountType,
            BigDecimal discountValue,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean removed
    ) {
        this.organization = organization;
        this.optionalFeature = optionalFeature;
        this.discount = new DiscountInfo(discountType, discountValue);
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.removed = removed;
    }
}
