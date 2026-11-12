package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.InspectionDisputeStatus;
import com.eformworks.signstage.backend.feature.order.entity.InspectionStatus;
import com.eformworks.signstage.backend.feature.order.entity.OrderInspection;
import com.eformworks.signstage.backend.feature.order.model.PartnerInspectionStats;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderInspectionRepository extends JpaRepository<OrderInspection, Long> {

    Optional<OrderInspection> findByPartnerOrderId(Long partnerOrderId);

    @Query(
            value = """
                    select i from OrderInspection i
                    join fetch i.partnerOrder p
                    join fetch p.salesOrder
                    join fetch i.organization
                    where (:status is null or i.status = :status)
                      and (:organizationId is null or i.organization.id = :organizationId)
                      and (:disputeStatus is null or i.disputeStatus = :disputeStatus)
                    """,
            countQuery = """
                    select count(i) from OrderInspection i
                    where (:status is null or i.status = :status)
                      and (:organizationId is null or i.organization.id = :organizationId)
                      and (:disputeStatus is null or i.disputeStatus = :disputeStatus)
                    """
    )
    Page<OrderInspection> search(
            @Param("status") InspectionStatus status,
            @Param("organizationId") Long organizationId,
            @Param("disputeStatus") InspectionDisputeStatus disputeStatus,
            Pageable pageable
    );

    @Query("""
            select i from OrderInspection i
            join fetch i.partnerOrder
            where i.status = com.eformworks.signstage.backend.feature.order.entity.InspectionStatus.PENDING
              and i.dueDate < :today
            """)
    List<OrderInspection> findPendingDueBefore(@Param("today") LocalDate today);

    /**
     * 정산할 검수 — 정산월 말일까지 승인됐고 아직 지급에 묶이지 않은 건. 이의 제기 중인 건과, 감액됐는데
     * 이의 기간(승인일 ≥ {@code disputeWindowFrom})이 남은 건은 빼고 다음 정산으로 넘긴다(D13).
     */
    @Query("""
            select i from OrderInspection i
            join fetch i.partnerOrder
            join fetch i.organization
            where i.status = com.eformworks.signstage.backend.feature.order.entity.InspectionStatus.APPROVED
              and i.approvedDate <= :to
              and i.partnerPayout is null
              and (i.disputeStatus is null
                   or i.disputeStatus = com.eformworks.signstage.backend.feature.order.entity.InspectionDisputeStatus.RESOLVED)
              and not (i.deductionAmount > 0 and i.disputeStatus is null and i.approvedDate >= :disputeWindowFrom)
            order by i.organization.id, i.approvedDate, i.id
            """)
    List<OrderInspection> findSettleableUntil(@Param("to") LocalDate to, @Param("disputeWindowFrom") LocalDate disputeWindowFrom);

    @Query("""
            select i from OrderInspection i
            join fetch i.partnerOrder p
            join fetch p.salesOrder
            join fetch i.organization
            where i.partnerPayout.id = :partnerPayoutId
            order by i.approvedDate, i.id
            """)
    List<OrderInspection> findAllByPartnerPayoutId(@Param("partnerPayoutId") Long partnerPayoutId);

    /** 파트너별 승인된 검수 집계(감액·하자·지체·승인 간주·평점). */
    @Query("""
            select new com.eformworks.signstage.backend.feature.order.model.PartnerInspectionStats(
                i.organization.id,
                count(i),
                sum(case when i.approvalType = com.eformworks.signstage.backend.feature.order.entity.InspectionApprovalType.DEEMED then 1 else 0 end),
                sum(case when i.defectNote is not null then 1 else 0 end),
                sum(case when i.delayNote is not null then 1 else 0 end),
                sum(case when i.deductionAmount > 0 then 1 else 0 end),
                sum(i.deductionAmount),
                avg(i.performanceRating),
                count(i.performanceRating)
            )
            from OrderInspection i
            where i.status = com.eformworks.signstage.backend.feature.order.entity.InspectionStatus.APPROVED
            group by i.organization.id
            """)
    List<PartnerInspectionStats> aggregateApprovedByOrganization();
}
