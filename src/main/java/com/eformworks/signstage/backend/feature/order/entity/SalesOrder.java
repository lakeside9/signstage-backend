package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.core.money.CurrencyPolicy;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.order.model.ScopeExtras;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랫폼 수주(고객 견적·계약) — signstage-docs business/platform-order-partner-subcontract-review.md
 * 3장 1~2단계, 5장. 견적(QUOTED) 동안에는 범위·금액을 다시 계산해 고칠 수 있고, 고객이 수락하면
 * (ACCEPTED) 금액과 범위가 고정된다. 수락 후에는 범위 변경({@link SalesOrderChange}, D08)으로만 바꾼다.
 *
 * <p>범위는 플랜(제공 범위, D07)과 추가 옵션 줄({@link SalesOrderLine})로 표현한다.
 * {@code tabletCount}·{@code onsiteStaffPersonDays}는 플랜 포함 수량과 옵션 수량을 더한 값으로,
 * 발주 금액 계산(6.2절)의 입력이다. 금액은 공급가 합계(할인 포함)·부가세·합계를 스냅샷한다.
 * {@code estimatedPartnerAmount}는 견적 시점 기본 단가표로 계산한 예상 발주 금액(마진 검사용)이다.
 */
@Entity
@Table(name = "sales_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceRegion region;

    @Column(length = 500)
    private String location;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "currency_fraction_digits", nullable = false)
    private short currencyFractionDigits;

    @Column(name = "currency_rounding_mode", nullable = false, length = 20)
    private String currencyRoundingMode;

    @Column(name = "tablet_count", nullable = false)
    private int tabletCount;

    @Column(name = "onsite_staff_person_days", nullable = false)
    private int onsiteStaffPersonDays;

    /** 예비 태블릿 — 발주에만 넣고 고객에게 청구하지 않는다(D23). */
    @Column(name = "spare_tablet_count", nullable = false)
    private int spareTabletCount;

    /** 본행사와 다른 날 리허설의 현장 인력 인·일(D24). */
    @Column(name = "rehearsal_staff_person_days", nullable = false)
    private int rehearsalStaffPersonDays;

    /** 관리자가 정한 숙박 박수·인원(D26). */
    @Column(name = "lodging_nights", nullable = false)
    private int lodgingNights;

    @Column(name = "lodging_persons", nullable = false)
    private int lodgingPersons;

    @Column(name = "supply_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal supplyAmount;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "discount_reason", length = 500)
    private String discountReason;

    @Column(name = "estimated_partner_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal estimatedPartnerAmount;

    @Column(name = "quote_valid_until", nullable = false)
    private LocalDate quoteValidUntil;

    /** 고객이 로그인 없이 견적을 열람·수락하는 링크 토큰(D02). */
    @Column(name = "quote_access_token", nullable = false, unique = true, length = 64)
    private String quoteAccessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesOrderStatus status;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_by_name", length = 100)
    private String acceptedByName;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_channel", length = 20)
    private SalesOrderAcceptanceChannel acceptanceChannel;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(length = 1000)
    private String memo;

    @Builder
    private SalesOrder(Customer customer, String currencyCode, String quoteAccessToken) {
        this.customer = customer;
        this.currencyCode = currencyCode;
        this.currencyFractionDigits = (short) Currency.getInstance(currencyCode).getDefaultFractionDigits();
        this.currencyRoundingMode = RoundingMode.HALF_UP.name();
        this.quoteAccessToken = quoteAccessToken;
        this.status = SalesOrderStatus.QUOTED;
    }

    /** 견적 내용을 통째로 바꾼다 — 줄({@link SalesOrderLine})은 서비스가 지우고 다시 만든다. */
    public void updateQuote(
            String title, BillingPlan billingPlan, ServiceRegion region, String location,
            LocalDateTime startsAt, LocalDateTime endsAt, LocalDate quoteValidUntil, String memo
    ) {
        this.title = title.trim();
        this.billingPlan = billingPlan;
        this.region = region;
        this.location = location == null || location.isBlank() ? null : location.trim();
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.quoteValidUntil = quoteValidUntil;
        this.memo = memo == null || memo.isBlank() ? null : memo.trim();
    }

    public void applyAmounts(
            int tabletCount, int onsiteStaffPersonDays, ScopeExtras extras, BigDecimal supplyAmount, BigDecimal vatAmount,
            String discountReason, BigDecimal estimatedPartnerAmount
    ) {
        this.tabletCount = tabletCount;
        this.onsiteStaffPersonDays = onsiteStaffPersonDays;
        this.spareTabletCount = extras.spareTabletCount();
        this.rehearsalStaffPersonDays = extras.rehearsalStaffPersonDays();
        this.lodgingNights = extras.lodgingNights();
        this.lodgingPersons = extras.lodgingPersons();
        this.supplyAmount = supplyAmount;
        this.vatAmount = vatAmount;
        this.totalAmount = supplyAmount.add(vatAmount);
        this.discountReason = discountReason == null || discountReason.isBlank() ? null : discountReason.trim();
        this.estimatedPartnerAmount = estimatedPartnerAmount;
    }

    /** 수락 후 범위 변경(D08)을 반영한다 — 플랜·권역은 그대로 두고 장소·일정과 금액만 바꾼다. */
    public void applyChange(SalesOrderChange change) {
        this.location = change.getLocation();
        this.startsAt = change.getStartsAt();
        this.endsAt = change.getEndsAt();
        applyAmounts(
                change.getTabletCount(), change.getOnsiteStaffPersonDays(), change.scopeExtras(), change.getSupplyAmount(),
                change.getVatAmount(), change.getDiscountReason(), change.getEstimatedPartnerAmount()
        );
    }

    public ScopeExtras scopeExtras() {
        return new ScopeExtras(spareTabletCount, rehearsalStaffPersonDays, lodgingNights, lodgingPersons);
    }

    public void accept(String acceptedByName, SalesOrderAcceptanceChannel channel) {
        this.status = SalesOrderStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
        this.acceptedByName = acceptedByName.trim();
        this.acceptanceChannel = channel;
    }

    public void cancel(String cancellationReason) {
        this.status = SalesOrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = cancellationReason;
    }

    public boolean isQuoteExpired(LocalDate today) {
        return today.isAfter(quoteValidUntil);
    }

    public CurrencyPolicy currencyPolicy() {
        return new CurrencyPolicy(currencyCode, currencyFractionDigits, RoundingMode.valueOf(currencyRoundingMode));
    }
}
