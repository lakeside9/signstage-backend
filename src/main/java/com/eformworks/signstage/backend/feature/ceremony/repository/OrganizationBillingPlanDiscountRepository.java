package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationBillingPlanDiscountRepository extends JpaRepository<OrganizationBillingPlanDiscount, Long> {

    Optional<OrganizationBillingPlanDiscount> findByOrganizationIdAndBillingPlanId(Long organizationId, Long billingPlanId);

    /** 조직별 할인 관리 화면이 이 조직에 걸린 오버라이드를 한 번에 보여주는 데 쓴다. */
    List<OrganizationBillingPlanDiscount> findAllByOrganizationId(Long organizationId);
}
