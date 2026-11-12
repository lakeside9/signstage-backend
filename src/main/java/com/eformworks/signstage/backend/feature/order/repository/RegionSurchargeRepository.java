package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.RegionSurcharge;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionSurchargeRepository extends JpaRepository<RegionSurcharge, Long> {

    @Query("select r from RegionSurcharge r order by r.region, r.period.effectiveFrom desc")
    List<RegionSurcharge> findAllOrdered();

    @Query("""
            select r from RegionSurcharge r
            where r.region = :region
              and r.period.effectiveFrom <= :asOfDate
              and (r.period.effectiveTo is null or r.period.effectiveTo >= :asOfDate)
            """)
    Optional<RegionSurcharge> findEffective(@Param("region") ServiceRegion region, @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            select count(r) > 0 from RegionSurcharge r
            where r.region = :region
              and r.period.effectiveFrom <= :to
              and (r.period.effectiveTo is null or r.period.effectiveTo >= :from)
              and (:excludeId is null or r.id <> :excludeId)
            """)
    boolean existsOverlap(
            @Param("region") ServiceRegion region,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("excludeId") Long excludeId
    );
}
