package com.eformworks.signstage.backend.feature.order.repository;

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
                    """,
            countQuery = """
                    select count(i) from OrderInspection i
                    where (:status is null or i.status = :status)
                      and (:organizationId is null or i.organization.id = :organizationId)
                    """
    )
    Page<OrderInspection> search(
            @Param("status") InspectionStatus status,
            @Param("organizationId") Long organizationId,
            Pageable pageable
    );

    @Query("""
            select i from OrderInspection i
            join fetch i.partnerOrder
            where i.status = com.eformworks.signstage.backend.feature.order.entity.InspectionStatus.PENDING
              and i.dueDate < :today
            """)
    List<OrderInspection> findPendingDueBefore(@Param("today") LocalDate today);

    /** 정산할 검수 — 승인일이 기간 안이고 아직 지급에 묶이지 않은 건. */
    @Query("""
            select i from OrderInspection i
            join fetch i.partnerOrder
            join fetch i.organization
            where i.status = com.eformworks.signstage.backend.feature.order.entity.InspectionStatus.APPROVED
              and i.approvedDate between :from and :to
              and i.partnerPayout is null
            order by i.organization.id, i.approvedDate, i.id
            """)
    List<OrderInspection> findUnsettledApprovedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

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
