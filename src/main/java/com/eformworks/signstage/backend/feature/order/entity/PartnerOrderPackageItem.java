package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 발주 시점의 패키지 기본 포함 구성 — signstage-docs business/platform-order-pending-items.md I05(2026-11-13).
 * 파트너 발주함은 이 복사본을 보여줘서, 발주 뒤 플랜 구성이 바뀌어도 파트너가 받은 범위가 그대로다.
 */
@Entity
@Table(name = "partner_order_package_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerOrderPackageItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_order_id", nullable = false)
    private PartnerOrder partnerOrder;

    @Column(nullable = false, length = 200)
    private String name;

    /** 실제 포함 개수(묶음 수 × 판매 단위). */
    @Column(nullable = false)
    private int quantity;

    @Column(name = "quantity_unit", nullable = false, length = 20)
    private String quantityUnit;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Builder
    private PartnerOrderPackageItem(PartnerOrder partnerOrder, String name, int quantity, String quantityUnit, int displayOrder) {
        this.partnerOrder = partnerOrder;
        this.name = name;
        this.quantity = quantity;
        this.quantityUnit = quantityUnit;
        this.displayOrder = displayOrder;
    }
}
