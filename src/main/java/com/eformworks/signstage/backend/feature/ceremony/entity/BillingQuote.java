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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 확정 견적 헤더 — signstage-docs business/currency-tax-internationalization-review.md 9장
 * 결정(2026-09-10 구현). {@code CeremonyService#calculateEstimatedTotal}(예상 청구 금액)이
 * 조회 시점 계산이라면, 이 엔티티는 그 계산 결과를 특정 시점에 **스냅샷으로 고정**한 것이다 —
 * 이후 카탈로그·세금 정책·할인이 바뀌어도 이 행은 절대 바뀌지 않는다(append-only, update/delete
 * 경로 없음). {@code Ceremony} 하나가 여러 버전의 견적을 가질 수 있다(재견적) — 새 버전을
 * 만들어도 이전 버전을 지우거나 자동으로 무효화하지 않는다(과거 견적도 그대로 이력으로 남긴다).
 *
 * <p>{@code BaseEntity}를 쓰지 않는다 — 이 테이블은 {@code updated_by}/{@code updated_at}
 * 컬럼이 없다(2026-09-04 원 마이그레이션부터 순수 append-only로 설계됨, 수정 자체가 없으므로
 * "마지막 수정자"라는 개념이 성립하지 않는다). 상태 변경(무효화)은 이 행을 고치는 대신
 * {@link BillingQuoteStatusEvent}에 새 이벤트를 추가하는 것으로 표현한다.
 */
@Entity
@Table(name = "billing_quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class BillingQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    /** 이 Ceremony 안에서의 버전 번호 — 1부터 시작, 재견적할 때마다 1씩 증가. */
    @Column(nullable = false)
    private Integer version;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "currency_fraction_digits", nullable = false)
    private Short currencyFractionDigits;

    @Column(name = "currency_rounding_mode", nullable = false, length = 20)
    private String currencyRoundingMode;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    /** subtotal(품목 할인 적용 후) - netAmount — 행사 건별 재량 할인 총액. */
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    /** 이 계산이 실제로 수행된 시각(UTC) — {@code createdAt}과 보통 같지만 의미가 다르다. */
    @Column(name = "pricing_calculated_at", nullable = false)
    private LocalDateTime pricingCalculatedAt;

    /** 세금 정책 유효기간 판정에 쓴 기준일(Ceremony 타임존 기준 "오늘"). */
    @Column(name = "tax_point_date", nullable = false)
    private LocalDate taxPointDate;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private BillingQuote(
            Ceremony ceremony,
            Integer version,
            String currencyCode,
            Short currencyFractionDigits,
            String currencyRoundingMode,
            BigDecimal netAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal grossAmount,
            LocalDateTime pricingCalculatedAt,
            LocalDate taxPointDate
    ) {
        this.ceremony = ceremony;
        this.version = version;
        this.currencyCode = currencyCode;
        this.currencyFractionDigits = currencyFractionDigits;
        this.currencyRoundingMode = currencyRoundingMode;
        this.netAmount = netAmount;
        this.discountAmount = discountAmount;
        this.taxAmount = taxAmount;
        this.grossAmount = grossAmount;
        this.pricingCalculatedAt = pricingCalculatedAt;
        this.taxPointDate = taxPointDate;
    }
}
