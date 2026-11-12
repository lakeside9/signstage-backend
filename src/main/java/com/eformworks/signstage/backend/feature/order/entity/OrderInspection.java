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

    /** 감액 유형(계약 기준표, D13). 감액이 있으면 필수다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "deduction_type", length = 40)
    private InspectionDeductionType deductionType;

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

    /** 파트너의 감액 이의 제기(D13). 제기 중(OPEN)이면 정산하지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", length = 20)
    private InspectionDisputeStatus disputeStatus;

    @Column(name = "dispute_reason", length = 1000)
    private String disputeReason;

    @Column(name = "disputed_at")
    private LocalDateTime disputedAt;

    @Column(name = "disputed_by")
    private Long disputedBy;

    @Column(name = "dispute_resolution_note", length = 1000)
    private String disputeResolutionNote;

    @Column(name = "dispute_resolved_at")
    private LocalDateTime disputeResolvedAt;

    @Column(name = "dispute_resolved_by")
    private Long disputeResolvedBy;

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
            InspectionDeductionType deductionType,
            Integer performanceRating
    ) {
        this.performanceRating = performanceRating;
        this.deductionType = deductionType;
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

    /** 이의 제기 마감일 — 승인일 + 이의 기간. 승인 전이거나 감액이 없으면 null(제기할 수 없다). */
    public LocalDate disputeDeadline(int disputeDays) {
        if (status != InspectionStatus.APPROVED || deductionAmount.signum() == 0) {
            return null;
        }
        return approvedDate.plusDays(disputeDays);
    }

    /** 감액이 있고, 마감 전이고, 아직 제기하지 않았고, 정산에 묶이지 않았으면 이의를 제기할 수 있다. */
    public boolean canDispute(LocalDate today, int disputeDays) {
        LocalDate deadline = disputeDeadline(disputeDays);
        return deadline != null && !today.isAfter(deadline) && disputeStatus == null && partnerPayout == null;
    }

    public boolean isDisputeOpen() {
        return disputeStatus == InspectionDisputeStatus.OPEN;
    }

    public void openDispute(Long userId, String reason) {
        this.disputeStatus = InspectionDisputeStatus.OPEN;
        this.disputeReason = reason;
        this.disputedBy = userId;
        this.disputedAt = LocalDateTime.now();
    }

    /**
     * 이의를 조정해 감액을 다시 정한다. 지급 대상 금액이 바뀐 만큼(새 금액 − 이전 금액)을 돌려준다 —
     * 원장에 조정으로 남긴다.
     */
    public BigDecimal resolveDispute(
            Long userId,
            BigDecimal deductionAmount,
            String deductionReason,
            InspectionDeductionType deductionType,
            String resolutionNote
    ) {
        BigDecimal previousPayable = payableAmount;
        this.deductionAmount = deductionAmount;
        this.deductionReason = deductionReason;
        this.deductionType = deductionType;
        this.payableAmount = partnerOrder.getTotalAmount().subtract(deductionAmount);
        this.disputeStatus = InspectionDisputeStatus.RESOLVED;
        this.disputeResolutionNote = resolutionNote;
        this.disputeResolvedBy = userId;
        this.disputeResolvedAt = LocalDateTime.now();
        return payableAmount.subtract(previousPayable);
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
