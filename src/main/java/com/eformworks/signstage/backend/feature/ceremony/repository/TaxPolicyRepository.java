package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.TaxPolicy;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxPolicyRepository extends JpaRepository<TaxPolicy, Long> {

    @Query("""
            select policy
            from TaxPolicy policy
            where policy.countryCode = :countryCode
              and policy.taxCode = :taxCode
              and policy.active = true
              and policy.effectiveFrom <= :taxPointDate
              and (policy.effectiveTo is null or policy.effectiveTo >= :taxPointDate)
            """)
    Optional<TaxPolicy> findEffectivePolicy(
            @Param("countryCode") String countryCode,
            @Param("taxCode") String taxCode,
            @Param("taxPointDate") LocalDate taxPointDate
    );
}
