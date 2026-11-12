package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.PartnerRateCard;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code organization}이 null인 행이 기본 단가표다. */
public interface PartnerRateCardRepository extends JpaRepository<PartnerRateCard, Long> {

    @Query("""
            select c from PartnerRateCard c
            left join fetch c.organization
            order by c.organization.id, c.period.effectiveFrom desc
            """)
    List<PartnerRateCard> findAllWithOrganization();

    @Query("""
            select c from PartnerRateCard c
            where c.organization is null
              and c.period.effectiveFrom <= :asOfDate
              and (c.period.effectiveTo is null or c.period.effectiveTo >= :asOfDate)
            """)
    Optional<PartnerRateCard> findEffectiveDefault(@Param("asOfDate") LocalDate asOfDate);

    @Query("""
            select c from PartnerRateCard c
            where c.organization.id = :organizationId
              and c.period.effectiveFrom <= :asOfDate
              and (c.period.effectiveTo is null or c.period.effectiveTo >= :asOfDate)
            """)
    Optional<PartnerRateCard> findEffectiveForPartner(
            @Param("organizationId") Long organizationId,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query("""
            select count(c) > 0 from PartnerRateCard c
            where ((:organizationId is null and c.organization is null) or c.organization.id = :organizationId)
              and c.period.effectiveFrom <= :to
              and (c.period.effectiveTo is null or c.period.effectiveTo >= :from)
              and (:excludeId is null or c.id <> :excludeId)
            """)
    boolean existsOverlap(
            @Param("organizationId") Long organizationId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("excludeId") Long excludeId
    );
}
