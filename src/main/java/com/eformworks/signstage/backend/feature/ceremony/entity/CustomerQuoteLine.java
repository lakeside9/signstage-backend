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
 * {@link CustomerQuote} 한 줄 — {@code lineType}이 둘로 갈린다:
 * <ul>
 *   <li>{@code SYSTEM_USAGE} — 시스템 사용료 전체를 합친 단일 줄이다({@code itemId}는 null,
 *   특정 단위 상품 하나를 가리키지 않는다). {@code customerAmount}는 원가+마진(헤더의
 *   {@code systemUsageCustomerAmount}와 같은 값).</li>
 *   <li>{@code EQUIPMENT_PERSONNEL} — 실제 구매한 장비/인력 단위 상품 하나당 한 줄. 파트너가
 *   견적 생성 시 직접 입력한 {@code customerUnitAmount}(플랫폼 판매가와 무관)를 그대로
 *   스냅샷한다. {@code referenceCostUnitAmount}는 파트너가 실제로 플랫폼에 낸 단가(참고용,
 *   화면에 나란히 보여주기 위한 것 — 계산에는 관여하지 않는다).</li>
 * </ul>
 */
@Entity
@Table(name = "customer_quote_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CustomerQuoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_quote_id", nullable = false)
    private CustomerQuote customerQuote;

    /** {@code SYSTEM_USAGE} | {@code EQUIPMENT_PERSONNEL}. */
    @Column(name = "line_type", nullable = false, length = 30)
    private String lineType;

    /** {@code UnitProduct.id} — FK 없음(스냅샷 원칙). {@code SYSTEM_USAGE} 줄은 null. */
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    /** 파트너가 플랫폼에 낸 참고 원가 단가 — {@code EQUIPMENT_PERSONNEL}만, 계산에 미사용. */
    @Column(name = "reference_cost_unit_amount", precision = 19, scale = 4)
    private BigDecimal referenceCostUnitAmount;

    /** 실고객에게 청구하는 단가. */
    @Column(name = "customer_unit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal customerUnitAmount;

    /** 이 줄의 합계(세전). */
    @Column(name = "customer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal customerAmount;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private CustomerQuoteLine(
            CustomerQuote customerQuote,
            String lineType,
            Long itemId,
            String itemName,
            Integer quantity,
            BigDecimal referenceCostUnitAmount,
            BigDecimal customerUnitAmount,
            BigDecimal customerAmount
    ) {
        this.customerQuote = customerQuote;
        this.lineType = lineType;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.referenceCostUnitAmount = referenceCostUnitAmount;
        this.customerUnitAmount = customerUnitAmount;
        this.customerAmount = customerAmount;
    }
}
