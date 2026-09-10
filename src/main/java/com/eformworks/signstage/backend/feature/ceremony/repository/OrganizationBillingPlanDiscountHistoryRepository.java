package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscountHistory;
import java.util.List;

public interface OrganizationBillingPlanDiscountHistoryRepository extends AppendOnlyRepository<OrganizationBillingPlanDiscountHistory, Long> {

    /** 최신순 — 설정(생성/수정) 시점마다, 그리고 제거 시점에 1건씩(removed=true) 쌓인다. */
    List<OrganizationBillingPlanDiscountHistory> findAllByOrganizationIdAndBillingPlanIdOrderByCreatedAtDesc(
            Long organizationId, Long billingPlanId
    );

    /**
     * 과금 플랜 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 오버라이드는 하드 삭제될 수 있어
     * {@link OrganizationBillingPlanDiscountRepository#existsByBillingPlanId}만으로는 "이미
     * 제거된 오버라이드"를 놓친다. 이 이력 테이블은 append-only라(제거 이벤트도 행으로 남는다)
     * 존재 여부만으로 "한 번이라도 이 조직×플랜 오버라이드가 설정된 적이 있는지"를 영구히 알 수
     * 있다.
     */
    boolean existsByBillingPlanId(Long billingPlanId);
}
