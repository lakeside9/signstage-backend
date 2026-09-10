package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriodHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitProductPricePeriodHistoryRepository extends JpaRepository<UnitProductPricePeriodHistory, Long> {

    List<UnitProductPricePeriodHistory> findAllByUnitProductIdOrderByCreatedAtDesc(Long unitProductId);

    /** 사용 이력이 전혀 없는 단위 상품을 완전히 삭제할 때, 그 상품 자신의 가격 기간 이력까지 함께 지운다. */
    void deleteAllByUnitProductId(Long unitProductId);
}
