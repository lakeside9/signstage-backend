package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductHistory;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitProductHistoryRepository extends AppendOnlyRepository<UnitProductHistory, Long> {

    /** 이력 조회용(최신순). */
    List<UnitProductHistory> findAllByUnitProductIdOrderByCreatedAtDesc(Long unitProductId);

    /**
     * append-only라 일반 delete를 노출하지 않지만, 사용 이력이 전혀 없는 단위 상품을 완전히
     * 삭제할 때는 그 상품 자신의 이력까지 함께 지워야 FK 위반 없이 부모 행을 지울 수 있다 —
     * {@code UnitProductService#deleteUnitProduct} 전용 예외적 경로.
     */
    @Modifying
    @Query("delete from UnitProductHistory h where h.unitProduct.id = :unitProductId")
    void deleteAllByUnitProductId(@Param("unitProductId") Long unitProductId);
}
