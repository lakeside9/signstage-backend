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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 조직×용량 추가구매 상품 할인 오버라이드({@link OrganizationCapacityAddOnDiscount})의 변경
 * 이력. {@link OrganizationBillingPlanDiscountHistory}와 같은 패턴·같은 이유(오버라이드 하드
 * 삭제 대응)다.
 */
@Entity
@Table(name = "organization_capacity_addon_discount_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class OrganizationCapacityAddOnDiscountHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capacity_addon_id", nullable = false)
    private CapacityAddOn capacityAddOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    /** true면 이 행이 "오버라이드 제거" 이벤트다 — discountType/discountValue는 제거 직전 값. */
    @Column(nullable = false)
    private boolean removed;

    @Builder
    private OrganizationCapacityAddOnDiscountHistory(
            Organization organization,
            CapacityAddOn capacityAddOn,
            DiscountType discountType,
            BigDecimal discountValue,
            boolean removed
    ) {
        this.organization = organization;
        this.capacityAddOn = capacityAddOn;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.removed = removed;
    }
}
