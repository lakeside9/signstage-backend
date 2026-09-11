package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductCartLine;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyUnitProductCartLineRepository extends JpaRepository<CeremonyUnitProductCartLine, Long> {

    /** 장바구니 조회 화면용 — 담은 순서대로. */
    List<CeremonyUnitProductCartLine> findAllByCeremonyIdOrderByIdAsc(Long ceremonyId);

    /** 담기(upsert)·수정·삭제 전부 이 조회로 기존 줄이 있는지부터 확인한다. */
    Optional<CeremonyUnitProductCartLine> findByCeremonyIdAndUnitProductId(Long ceremonyId, Long unitProductId);

    /**
     * "구매하기" 성공 직후 이 행사의 장바구니를 비운다. 플랜이 확정되지 않은(DRAFT) 행사
     * 삭제 시에도 같은 메서드로 장바구니를 함께 지운다(FK 순서상
     * {@code CeremonyUnitProductPurchaseRepository.deleteAllByCeremonyId}보다 먼저 호출해도,
     * 나중에 호출해도 무방하다 — 이 테이블을 참조하는 다른 테이블이 없다).
     */
    void deleteAllByCeremonyId(Long ceremonyId);
}
