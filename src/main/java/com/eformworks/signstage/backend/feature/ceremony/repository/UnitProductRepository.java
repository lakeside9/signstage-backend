package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitProductRepository extends JpaRepository<UnitProduct, Long> {

    List<UnitProduct> findAllByIdIn(List<Long> ids);

    /** 카탈로그 목록 조회용 — 표시 순서(displayOrder) 오름차순, 동률은 id 오름차순. */
    List<UnitProduct> findAllByOrderByDisplayOrderAscIdAsc();
}
