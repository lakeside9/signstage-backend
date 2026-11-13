package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
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

/**
 * 범위 변경 후 견적 줄 — {@link SalesOrderLine}과 같은 스냅샷이다. 반영할 때 수주 줄을 이 줄로 바꾼다.
 */
@Entity
@Table(name = "sales_order_change_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOrderChangeLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_change_id", nullable = false)
    private SalesOrderChange salesOrderChange;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private SalesOrderLineType lineType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id")
    private UnitProduct unitProduct;

    @Column(nullable = false, length = 200)
    private String name;

    /** 견적서에 줄 이름 아래 보이는 설명 — 저장 시점의 단위 상품 견적 설명(패키지 줄은 포함 구성)을 복사한다. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    @Column(name = "tax_rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRatePercent;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 단가를 가져온 고객 가격 행 — 쓰인 가격은 삭제할 수 없다. 할인·할증·숙박 줄은 null이다. */
    @Column(name = "customer_price_id")
    private Long customerPriceId;

    /** 직접 입력 항목(CUSTOM_ITEM)의 발주 단가 — 수량을 곱해 발주 금액에 더한다. 다른 줄은 null이다. */
    @Column(name = "partner_unit_price", precision = 19, scale = 4)
    private BigDecimal partnerUnitPrice;

    @Builder
    private SalesOrderChangeLine(
            SalesOrderChange salesOrderChange, SalesOrderLineType lineType, UnitProduct unitProduct, String name, String description,
            int quantity, BigDecimal unitPrice, BigDecimal amount, String taxCode, BigDecimal taxRatePercent,
            BigDecimal taxAmount, int displayOrder, Long customerPriceId, BigDecimal partnerUnitPrice
    ) {
        this.salesOrderChange = salesOrderChange;
        this.lineType = lineType;
        this.unitProduct = unitProduct;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.taxCode = taxCode;
        this.taxRatePercent = taxRatePercent;
        this.taxAmount = taxAmount;
        this.displayOrder = displayOrder;
        this.customerPriceId = customerPriceId;
        this.partnerUnitPrice = partnerUnitPrice;
    }
}
