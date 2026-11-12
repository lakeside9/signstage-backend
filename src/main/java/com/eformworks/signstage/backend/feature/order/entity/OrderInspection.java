package com.eformworks.signstage.backend.feature.order.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발주별 검수 — signstage-docs business/platform-order-partner-subcontract-review.md D11·D13.
 *
 * <ul>
 *   <li>수락한 발주의 행사가 완료되면 PENDING으로 만들고, 검수 기한은 완료일(현지 날짜) + 검수 기간이다.</li>
 *   <li>플랫폼이 하자·지체를 기록하고 감액해 승인하거나(MANUAL), 기한이 지나면 감액 없이 승인으로
 *       본다(DEEMED, 승인일은 기한일).</li>
 *   <li>승인하면 지급 대상 금액(발주 금액 − 감액)이 정해지고 바뀌지 않는다. 승인월에 월 합산 지급에
 *       묶인다.</li>
 * </ul>
 */
@Entity
@Table(name = "order_inspections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderInspection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_order_id", nullable = false)
    private PartnerOrder partnerOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "ceremony_completed_at", nullable = false)
    private LocalDateTime ceremonyCompletedAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", length = 20)
    private InspectionApprovalType approvalType;

    @Column(name = "defect_note", length = 1000)
    private String defectNote;

    @Column(name = "delay_note", length = 1000)
    private String delayNote;

    @Column(name = "deduction_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal deductionAmount;

    @Column(name = "deduction_reason", length = 500)
    private String deductionReason;

    /** 플랫폼이 매긴 수행 평점(1~5, 선택) — 파트너 평가에 쓴다. 승인 간주 건은 null이다. */
    @Column(name = "performance_rating")
    private Integer performanceRating;

    /** 지급 대상 공급가(발주 금액 − 감액). 승인 전에는 null이다. */
    @Column(name = "payable_amount", precision = 19, scale = 4)
    private BigDecimal payableAmount;

    /** 지급월·지급 기한의 기준일(현지 날짜). 승인 간주면 검수 기한일이다. */
    @Column(name = "approved_date")
    private LocalDate approvedDate;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_payout_id")
    private PartnerPayout partnerPayout;

    @Builder
    private OrderInspection(PartnerOrder partnerOrder, LocalDateTime ceremonyCompletedAt, LocalDate dueDate) {
        this.partnerOrder = partnerOrder;
        this.organization = partnerOrder.getOrganization();
        this.ceremonyCompletedAt = ceremonyCompletedAt;
        this.dueDate = dueDate;
        this.status = InspectionStatus.PENDING;
        this.deductionAmount = BigDecimal.ZERO;
    }

    public boolean isPending() {
        return status == InspectionStatus.PENDING;
    }

    public boolean isOverdue(LocalDate today) {
        return isPending() && today.isAfter(dueDate);
    }

    public void approve(
            Long approvedBy,
            LocalDate approvedDate,
            String defectNote,
            String delayNote,
            BigDecimal deductionAmount,
            String deductionReason,
            Integer performanceRating
    ) {
        this.performanceRating = performanceRating;
        this.defectNote = defectNote;
        this.delayNote = delayNote;
        this.deductionAmount = deductionAmount;
        this.deductionReason = deductionReason;
        markApproved(InspectionApprovalType.MANUAL, approvedBy, approvedDate);
    }

    /** 검수 기한이 지나 승인으로 본다 — 감액 없이 발주 금액 전부가 지급 대상이다. */
    public void approveAsDeemed() {
        markApproved(InspectionApprovalType.DEEMED, null, dueDate);
    }

    public void assignPayout(PartnerPayout payout) {
        this.partnerPayout = payout;
    }

    public void releasePayout() {
        this.partnerPayout = null;
    }

    private void markApproved(InspectionApprovalType approvalType, Long approvedBy, LocalDate approvedDate) {
        this.status = InspectionStatus.APPROVED;
        this.approvalType = approvalType;
        this.approvedBy = approvedBy;
        this.approvedDate = approvedDate;
        this.approvedAt = LocalDateTime.now();
        this.payableAmount = partnerOrder.getTotalAmount().subtract(deductionAmount);
    }
}
