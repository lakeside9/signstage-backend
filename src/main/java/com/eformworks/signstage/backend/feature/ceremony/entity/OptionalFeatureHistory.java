package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

    @Embedded
    private CatalogPriceInfo priceInfo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "projector_effect", nullable = false)
    private boolean projectorEffect;

    @Column(name = "exclusivity_group", length = 50)
    private String exclusivityGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OptionalFeatureCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "paired_capacity_type", length = 20)
    private CapacityType pairedCapacityType;

    @Builder
    private OptionalFeatureHistory(OptionalFeature optionalFeature) {
        this.optionalFeature = optionalFeature;
        this.code = optionalFeature.getCode();
        this.name = optionalFeature.getName();
        this.priceInfo = optionalFeature.getPriceInfo();
        this.active = optionalFeature.isActive();
        this.projectorEffect = optionalFeature.isProjectorEffect();
        this.exclusivityGroup = optionalFeature.getExclusivityGroup();
        this.category = optionalFeature.getCategory();
        this.pairedCapacityType = optionalFeature.getPairedCapacityType();
    }
}
