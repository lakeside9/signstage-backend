package com.eformworks.signstage.backend.feature.ceremony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 확정 견적의 줄 하나 — signstage-docs
 * business/currency-tax-internationalization-review.md 9장. {@code CeremonyService
 * .QuoteLineDetail}(계산 결과)를 1:1로 그대로 옮겨 담는다. {@code itemId}는 {@code UnitProduct}를
 * FK 없이 가리킨다 — 확정 이후 그 단위 상품이 수정·삭제되더라도 이 줄의 값(이름/가격/세금)은
 * 절대 바뀌면 안 되므로, 라이브 조인이 아니라 그 순간의 값을 그대로 복제해 저장한다(이 프로젝트
 * 전반의 스냅샷 관례와 동일).
 */
@Entity
@Table(name = "billing_quote_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class BillingQuoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_quote_id", nullable = false)
    private BillingQuote billingQuote;

    /** {@code PLAN_UNIT_PRODUCT} | {@code UNIT_PRODUCT_PURCHASE}. */
    @Column(name = "line_type", nullable = false, length = 30)
    private String lineType;

    /** {@code UnitProduct.id} — FK 없음(위 클래스 설명 참고). */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_list_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitListAmount;

    @Column(name = "list_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal listAmount;

    /** 플랜 자체 할인의 이 줄 배분액(추가구매 줄은 항상 0 — 3.5절 "추가구매엔 할인 없음"). */
    @Column(name = "item_discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemDiscountAmount;

    /** 행사 건별 재량 할인의 이 줄 배분액. */
    @Column(name = "ceremony_discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal ceremonyDiscountAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    @Column(name = "tax_category", nullable = false, length = 20)
    private String taxCategory;

    @Column(name = "tax_rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRatePercent;

    @Column(name = "price_inclusion", nullable = false, length = 10)
    private String priceInclusion;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private BillingQuoteLine(
            BillingQuote billingQuote,
            String lineType,
            Long itemId,
            String itemName,
            Integer quantity,
            BigDecimal unitListAmount,
            BigDecimal listAmount,
            BigDecimal itemDiscountAmount,
            BigDecimal ceremonyDiscountAmount,
            BigDecimal netAmount,
            String taxCode,
            String taxCategory,
            BigDecimal taxRatePercent,
            String priceInclusion,
            BigDecimal taxAmount,
            BigDecimal grossAmount
    ) {
        this.billingQuote = billingQuote;
        this.lineType = lineType;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitListAmount = unitListAmount;
        this.listAmount = listAmount;
        this.itemDiscountAmount = itemDiscountAmount;
        this.ceremonyDiscountAmount = ceremonyDiscountAmount;
        this.netAmount = netAmount;
        this.taxCode = taxCode;
        this.taxCategory = taxCategory;
        this.taxRatePercent = taxRatePercent;
        this.priceInclusion = priceInclusion;
        this.taxAmount = taxAmount;
        this.grossAmount = grossAmount;
    }
}
