package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitProductRepository extends JpaRepository<UnitProduct, Long> {

    List<UnitProduct> findAllByIdIn(List<Long> ids);

    /** 카탈로그 목록 조회용 — 표시 순서(displayOrder) 오름차순, 동률은 id 오름차순. */
    List<UnitProduct> findAllByOrderByDisplayOrderAscIdAsc();

    /**
     * 현장지원 요청(관리자 견적) 앵커 상품 조회 — {@code UnitProductType.ONSITE_SUPPORT_REQUEST}
     * 는 정확히 1행만 존재해야 한다(마이그레이션이 시딩, 관리자 카탈로그 등록 화면에서 새로
     * 만들 수 없음). 여러 행이 있으면 id가 가장 작은 것을 쓴다.
     */
    Optional<UnitProduct> findFirstByTypeOrderByIdAsc(UnitProductType type);
}
