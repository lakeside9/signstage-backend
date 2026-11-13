package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.QuoteTemplate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteTemplateRepository extends JpaRepository<QuoteTemplate, Long> {

    @Query("select t from QuoteTemplate t order by t.period.effectiveFrom desc")
    List<QuoteTemplate> findAllOrdered();

    @Query("""
            select t from QuoteTemplate t
            where t.period.effectiveFrom <= :asOfDate
              and (t.period.effectiveTo is null or t.period.effectiveTo >= :asOfDate)
            """)
    Optional<QuoteTemplate> findEffective(@Param("asOfDate") LocalDate asOfDate);

    @Query("""
            select count(t) > 0 from QuoteTemplate t
            where t.period.effectiveFrom <= :to
              and (t.period.effectiveTo is null or t.period.effectiveTo >= :from)
              and (:excludeId is null or t.id <> :excludeId)
            """)
    boolean existsOverlap(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("excludeId") Long excludeId);
}
