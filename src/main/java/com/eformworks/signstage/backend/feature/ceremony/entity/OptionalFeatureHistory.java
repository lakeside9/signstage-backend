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
 * 선택옵션(OptionalFeature)의 값/사용여부 변경 이력. append-only다 — {@link BillingPlanHistory}와
 * 같은 패턴. {@code code}는 원본에서 불변이지만 조인 없이 이력만으로 표시할 수 있게 그대로
 * 스냅샷에 포함한다.
 */
@Entity
@Table(name = "optional_feature_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class OptionalFeatureHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OptionalFeatureCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "supply_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "projector_effect", nullable = false)
    private boolean projectorEffect;

    @Column(name = "exclusivity_group", length = 50)
    private String exclusivityGroup;

    @Builder
    private OptionalFeatureHistory(OptionalFeature optionalFeature) {
        this.optionalFeature = optionalFeature;
        this.code = optionalFeature.getCode();
        this.name = optionalFeature.getName();
        this.currencyCode = optionalFeature.getCurrencyCode();
        this.supplyPrice = optionalFeature.getSupplyPrice();
        this.salePrice = optionalFeature.getSalePrice();
        this.discountType = optionalFeature.getDiscountType();
        this.discountValue = optionalFeature.getDiscountValue();
        this.taxCode = optionalFeature.getTaxCode();
        this.active = optionalFeature.isActive();
        this.projectorEffect = optionalFeature.isProjectorEffect();
        this.exclusivityGroup = optionalFeature.getExclusivityGroup();
    }
}
