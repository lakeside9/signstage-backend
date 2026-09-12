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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 단위 상품(UnitProduct)의 이름/설명/배타그룹/분류/최대 구매 수량 변경 이력 — 기존 {@code OptionalFeatureHistory}/
 * {@code CapacityAddOnHistory} 통합. append-only다. {@code type}은 원본에서 불변이지만 조인
 * 없이 이력만으로 표시할 수 있게 그대로 스냅샷에 포함한다. 가격정보/사용여부 변경 이력은
 * {@link UnitProductPricePeriodHistory}가 담당한다.
 */
@Entity
@Table(name = "unit_product_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class UnitProductHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UnitProductType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitProductCategory category;

    @Column(name = "exclusivity_group", length = 50)
    private String exclusivityGroup;

    @Column(name = "max_purchase_quantity")
    private Integer maxPurchaseQuantity;

    /** {@link UnitProduct#isPlatformUsageFee()} 스냅샷(2026-09-12 신설 필드도 이력에 포함). */
    @Column(name = "is_platform_usage_fee", nullable = false)
    private boolean platformUsageFee;

    @Builder
    private UnitProductHistory(UnitProduct unitProduct) {
        this.unitProduct = unitProduct;
        this.type = unitProduct.getType();
        this.name = unitProduct.getName();
        this.description = unitProduct.getDescription();
        this.category = unitProduct.getCategory();
        this.exclusivityGroup = unitProduct.getExclusivityGroup();
        this.maxPurchaseQuantity = unitProduct.getMaxPurchaseQuantity();
        this.platformUsageFee = unitProduct.isPlatformUsageFee();
    }
}
