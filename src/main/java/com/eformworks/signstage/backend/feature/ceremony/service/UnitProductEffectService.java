package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.billing.port.UnitProductEffectPort;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinitionOption;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UnitProductEffectPort} 구현 — 이벤트 효과 묶음 단위 상품과 효과 정의의 N:N 매핑
 * ({@link CeremonyEffectDefinitionOption})을 관리한다. 호출하는 쪽(UnitProductService)의
 * 트랜잭션에 참여한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnitProductEffectService implements UnitProductEffectPort {

    private final CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    private final CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;

    @Override
    public List<Long> findEffectDefinitionIds(Long unitProductId) {
        return ceremonyEffectDefinitionOptionRepository.findAllByUnitProductId(unitProductId).stream()
                .map(option -> option.getEffectDefinition().getId())
                .toList();
    }

    /**
     * 생략/빈 배열이면 아무것도 만들지 않는다(생성 시 "빈 묶음으로 시작"). 효과 하나가 여러 묶음에
     * 속할 수 있는 N:N 매핑이라, 여기서 만드는 행은 이 단위 상품 쪽만 새로 추가한다 — 이미 다른
     * 묶음에 속한 효과라도 그대로 둔 채 이 상품에도 추가된다.
     *
     * <p>한 묶음에는 한 가지 trigger(개별 서명 확인 또는 전체 서명 완료)의 효과만 담는다(2026-11-13) —
     * 두 효과를 선택옵션으로 따로 팔고 적용하기 위해서다.
     */
    @Override
    @Transactional
    public void linkEffectDefinitions(UnitProduct unitProduct, List<Long> effectDefinitionIds) {
        if (effectDefinitionIds == null || effectDefinitionIds.isEmpty()) {
            return;
        }
        List<CeremonyEffectDefinition> definitions = resolveEffectDefinitions(effectDefinitionIds);
        if (definitions.stream().map(CeremonyEffectDefinition::getTriggerType).distinct().count() > 1) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_BUNDLE_TRIGGER_MIXED);
        }
        for (CeremonyEffectDefinition definition : definitions) {
            ceremonyEffectDefinitionOptionRepository.save(
                    CeremonyEffectDefinitionOption.builder().effectDefinition(definition).unitProduct(unitProduct).build()
            );
        }
    }

    /**
     * flush()가 반드시 필요하다 — deleteAllByUnitProductId는 파생 delete 쿼리라 DELETE SQL이 즉시
     * 나가지 않는데, CeremonyEffectDefinitionOption은 IDENTITY 채번이라 재저장 시 즉시 INSERT를
     * 실행한다. 그 사이 flush가 없으면 이번 교체에도 그대로 남는 효과(같은 effect_definition_id+
     * unit_product_id 조합)의 INSERT가 아직 DB에 남은 옛 행과 충돌해 uq_cedo_definition_product
     * 유니크 제약 위반으로 실패한다(BillingPlanService#updatePlan/
     * CeremonyEventService#applyOptionalFeatures와 같은 패턴, 2026-09-10에 발견해 고친 버그).
     */
    @Override
    @Transactional
    public void replaceEffectDefinitions(UnitProduct unitProduct, List<Long> effectDefinitionIds) {
        ceremonyEffectDefinitionOptionRepository.deleteAllByUnitProductId(unitProduct.getId());
        ceremonyEffectDefinitionOptionRepository.flush();
        linkEffectDefinitions(unitProduct, effectDefinitionIds);
    }

    /** 중복 없이 전부 존재하는지 확인하고 {@code CeremonyEffectDefinition} 목록으로 정규화한다. */
    private List<CeremonyEffectDefinition> resolveEffectDefinitions(List<Long> effectDefinitionIds) {
        List<Long> distinctIds = effectDefinitionIds.stream().distinct().toList();
        if (distinctIds.size() != effectDefinitionIds.size()) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        List<CeremonyEffectDefinition> found = ceremonyEffectDefinitionRepository.findAllById(distinctIds);
        if (found.size() != distinctIds.size()) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND);
        }
        return found;
    }
}
