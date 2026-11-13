package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수락된 수주의 범위 변경 — signstage-docs business/platform-order-partner-subcontract-review.md D08.
 * 플랫폼이 고객 변경 계약(견적 다시 계산)과 발주 변경(파트너 단가 스냅샷으로 다시 계산)을 한 건으로
 * 제안한다. 고객 수락과 파트너 동의(제안 때 수락된 발주가 있을 때만)를 모두 받으면 수주·발주·행사에
 * 반영한다. 플랜과 권역은 바꾸지 않는다 — 옵션 수량·영업 할인·장소·일정만 바꾼다.
 *
 * <p>변경 전·후 수량과 금액을 스냅샷해 두 번 계산하지 않는다. 새 견적 줄은 {@link SalesOrderChangeLine}에 있다.
 */
@Entity
@Table(name = "sales_order_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOrderChange extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    /** 제안 때 수락된 발주. 없으면 파트너 동의 없이 고객 수락만으로 반영한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_order_id")
    private PartnerOrder partnerOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesOrderChangeStatus status;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "before_tablet_count", nullable = false)
    private int beforeTabletCount;

    @Column(name = "before_wifi_count", nullable = false)
    private int beforeWifiCount;

    @Column(name = "before_onsite_staff_person_days", nullable = false)
    private int beforeOnsiteStaffPersonDays;

    @Column(name = "before_custom_item_partner_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal beforeCustomItemPartnerAmount;

    @Column(name = "before_spare_tablet_count", nullable = false)
    private int beforeSpareTabletCount;

    @Column(name = "before_rehearsal_staff_person_days", nullable = false)
    private int beforeRehearsalStaffPersonDays;

    @Column(name = "before_lodging_nights", nullable = false)
    private int beforeLodgingNights;

    @Column(name = "before_lodging_persons", nullable = false)
    private int beforeLodgingPersons;

    @Column(name = "before_supply_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal beforeSupplyAmount;

    @Column(name = "before_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal beforeTotalAmount;

    @Column(name = "before_partner_amount", precision = 19, scale = 4)
    private BigDecimal beforePartnerAmount;

    @Column(name = "tablet_count", nullable = false)
    private int tabletCount;

    @Column(name = "wifi_count", nullable = false)
    private int wifiCount;

    @Column(name = "onsite_staff_person_days", nullable = false)
    private int onsiteStaffPersonDays;

    /** 변경 후 직접 입력 항목의 발주 금액 합계. */
    @Column(name = "custom_item_partner_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal customItemPartnerAmount;

    /** 변경 후 예비 태블릿·리허설 별도일 인력·숙박(D23·D24·D26). */
    @Column(name = "spare_tablet_count", nullable = false)
    private int spareTabletCount;

    @Column(name = "rehearsal_staff_person_days", nullable = false)
    private int rehearsalStaffPersonDays;

    @Column(name = "lodging_nights", nullable = false)
    private int lodgingNights;

    @Column(name = "lodging_persons", nullable = false)
    private int lodgingPersons;

    /** 변경 견적 계산에 쓴 권역 할증 행. */
    @Column(name = "region_surcharge_id")
    private Long regionSurchargeId;

    @Column(name = "supply_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal supplyAmount;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "discount_reason", length = 500)
    private String discountReason;

    /** 변경 후 퍼센트 할인율(%). 정액 할인·할인 없음이면 NULL — 반영할 때 수주로 옮긴다. */
    @Column(name = "discount_rate_percent", precision = 5, scale = 2)
    private BigDecimal discountRatePercent;

    /** 변경 후 공급가 절사 단위. 절사하지 않으면 NULL이다. */
    @Column(name = "truncation_unit")
    private Integer truncationUnit;

    @Column(name = "estimated_partner_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal estimatedPartnerAmount;

    /** 변경 후 발주 금액 — 발주의 단가 스냅샷으로 계산한다. 수락된 발주가 없으면 null. */
    @Column(name = "partner_amount", precision = 19, scale = 4)
    private BigDecimal partnerAmount;

    @Column(length = 500)
    private String location;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "proposed_by", nullable = false)
    private Long proposedBy;

    @Column(name = "customer_accepted_by_name", length = 100)
    private String customerAcceptedByName;

    @Column(name = "customer_accepted_at")
    private LocalDateTime customerAcceptedAt;

    @Column(name = "partner_responded_by")
    private Long partnerRespondedBy;

    @Column(name = "partner_responded_at")
    private LocalDateTime partnerRespondedAt;

    @Column(name = "partner_rejection_reason", length = 500)
    private String partnerRejectionReason;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Builder
    private SalesOrderChange(
            SalesOrder salesOrder, PartnerOrder partnerOrder, String reason, int tabletCount, int wifiCount, int onsiteStaffPersonDays,
            ScopeExtras extras, BigDecimal customItemPartnerAmount, BigDecimal supplyAmount, BigDecimal vatAmount, String discountReason, BigDecimal estimatedPartnerAmount,
            BigDecimal discountRatePercent, Integer truncationUnit, BigDecimal partnerAmount, String location, LocalDateTime startsAt, LocalDateTime endsAt, Long proposedBy,
            Long regionSurchargeId
    ) {
        this.salesOrder = salesOrder;
        this.partnerOrder = partnerOrder;
        this.status = SalesOrderChangeStatus.PROPOSED;
        this.reason = reason;
        this.beforeTabletCount = salesOrder.getTabletCount();
        this.beforeWifiCount = salesOrder.getWifiCount();
        this.beforeOnsiteStaffPersonDays = salesOrder.getOnsiteStaffPersonDays();
        this.beforeCustomItemPartnerAmount = salesOrder.getCustomItemPartnerAmount();
        this.beforeSpareTabletCount = salesOrder.getSpareTabletCount();
        this.beforeRehearsalStaffPersonDays = salesOrder.getRehearsalStaffPersonDays();
        this.beforeLodgingNights = salesOrder.getLodgingNights();
        this.beforeLodgingPersons = salesOrder.getLodgingPersons();
        this.beforeSupplyAmount = salesOrder.getSupplyAmount();
        this.beforeTotalAmount = salesOrder.getTotalAmount();
        this.beforePartnerAmount = partnerOrder != null ? partnerOrder.getTotalAmount() : null;
        this.tabletCount = tabletCount;
        this.wifiCount = wifiCount;
        this.onsiteStaffPersonDays = onsiteStaffPersonDays;
        ScopeExtras after = extras != null ? extras : ScopeExtras.NONE;
        this.spareTabletCount = after.spareTabletCount();
        this.rehearsalStaffPersonDays = after.rehearsalStaffPersonDays();
        this.lodgingNights = after.lodgingNights();
        this.lodgingPersons = after.lodgingPersons();
        this.customItemPartnerAmount = customItemPartnerAmount != null ? customItemPartnerAmount : BigDecimal.ZERO;
        this.supplyAmount = supplyAmount;
        this.vatAmount = vatAmount;
        this.totalAmount = supplyAmount.add(vatAmount);
        this.discountReason = discountReason;
        this.discountRatePercent = discountRatePercent == null || discountRatePercent.signum() == 0 ? null : discountRatePercent;
        this.truncationUnit = truncationUnit;
        this.estimatedPartnerAmount = estimatedPartnerAmount;
        this.partnerAmount = partnerAmount;
        this.location = location;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.proposedBy = proposedBy;
        this.regionSurchargeId = regionSurchargeId;
    }

    public ScopeExtras beforeScopeExtras() {
        return new ScopeExtras(beforeSpareTabletCount, beforeRehearsalStaffPersonDays, beforeLodgingNights, beforeLodgingPersons);
    }

    public ScopeExtras scopeExtras() {
        return new ScopeExtras(spareTabletCount, rehearsalStaffPersonDays, lodgingNights, lodgingPersons);
    }

    public boolean isProposed() {
        return status == SalesOrderChangeStatus.PROPOSED;
    }

    public boolean needsPartnerConsent() {
        return partnerOrder != null;
    }

    public boolean isCustomerAccepted() {
        return customerAcceptedAt != null;
    }

    public boolean isPartnerAccepted() {
        return partnerRespondedAt != null && partnerRejectionReason == null;
    }

    /** 고객 수락과(필요하면) 파트너 동의를 모두 받았는지 — 받았으면 서비스가 반영한다. */
    public boolean isReadyToApply() {
        return isProposed() && isCustomerAccepted() && (!needsPartnerConsent() || isPartnerAccepted());
    }

    public void recordCustomerAcceptance(String acceptedByName) {
        this.customerAcceptedByName = acceptedByName;
        this.customerAcceptedAt = LocalDateTime.now();
    }

    public void acceptByPartner(Long userId) {
        this.partnerRespondedBy = userId;
        this.partnerRespondedAt = LocalDateTime.now();
    }

    public void rejectByPartner(Long userId, String reason) {
        this.status = SalesOrderChangeStatus.REJECTED;
        this.partnerRespondedBy = userId;
        this.partnerRespondedAt = LocalDateTime.now();
        this.partnerRejectionReason = reason;
    }

    public void markApplied() {
        this.status = SalesOrderChangeStatus.APPLIED;
        this.appliedAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        this.status = SalesOrderChangeStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }
}
