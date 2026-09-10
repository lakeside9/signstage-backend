package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinitionOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CeremonyEffectDefinitionOptionRepository extends JpaRepository<CeremonyEffectDefinitionOption, Long> {

    List<CeremonyEffectDefinitionOption> findAllByEffectDefinitionId(Long effectDefinitionId);

    List<CeremonyEffectDefinitionOption> findAllByUnitProductId(Long unitProductId);

    /** 단위 상품 여러 건의 "여는 효과 id" 목록을 한 번에 조회한다 — 구매 가능 목록 화면이 N+1 없이 표시하는 데 쓴다. */
    @Query("select o from CeremonyEffectDefinitionOption o where o.unitProduct.id in :unitProductIds")
    List<CeremonyEffectDefinitionOption> findAllByUnitProductIdIn(List<Long> unitProductIds);

    void deleteAllByUnitProductId(Long unitProductId);

    /** 효과 정의 여러 건의 "속한 묶음 id" 목록을 한 번에 조회한다 — 관리자 목록 화면이 N+1 없이 표시하는 데 쓴다. */
    @Query("select o from CeremonyEffectDefinitionOption o where o.effectDefinition.id in :effectDefinitionIds")
    List<CeremonyEffectDefinitionOption> findAllByEffectDefinitionIdIn(List<Long> effectDefinitionIds);

    /**
     * {@code effectDefinitionId}가 {@code unitProductIds} 중 하나에라도 속해 있으면 참이다 —
     * entitlement 판정(합집합)에 쓴다.
     */
    boolean existsByEffectDefinitionIdAndUnitProductIdIn(Long effectDefinitionId, List<Long> unitProductIds);
}
