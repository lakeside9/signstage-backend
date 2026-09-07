package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CeremonyEffectDefinitionRepository
        extends JpaRepository<CeremonyEffectDefinition, Long>, CeremonyEffectDefinitionRepositoryCustom {

    boolean existsByCode(String code);

    /** BE-CATALOG-03의 그룹 재정규화(위/아래 이동)에서 그룹 전체 행을 잠그고 순서를 다시 매길 때 사용한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select d from CeremonyEffectDefinition d "
                    + "where d.targetType = :targetType and d.triggerType = :triggerType "
                    + "order by d.displayOrder asc, d.id asc"
    )
    List<CeremonyEffectDefinition> findAllByGroupForUpdate(
            CeremonyEffectTarget targetType, CeremonyEffectTrigger triggerType
    );

    /** {@code /api/ceremony-effects} — 활성 + 사용자 노출 정의만. BE-CATALOG-03. */
    List<CeremonyEffectDefinition> findAllByEnabledTrueAndUserVisibleTrueOrderByTargetTypeAscTriggerTypeAscDisplayOrderAsc();
}
