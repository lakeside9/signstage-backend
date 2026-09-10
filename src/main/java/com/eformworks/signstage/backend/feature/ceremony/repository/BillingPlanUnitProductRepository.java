package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanUnitProduct;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanUnitProductRepository extends JpaRepository<BillingPlanUnitProduct, Long> {

    List<BillingPlanUnitProduct> findAllByBillingPlanId(Long billingPlanId);

    Optional<BillingPlanUnitProduct> findByBillingPlanIdAndUnitProductId(Long billingPlanId, Long unitProductId);

    void deleteAllByBillingPlanId(Long billingPlanId);

    /** 단위 상품 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 현재 어떤 플랜에 포함돼 있는지. */
    boolean existsByUnitProductId(Long unitProductId);
}
