package com.eformworks.signstage.backend.feature.billing.port;

import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import java.util.List;

/**
 * 이벤트 효과 묶음(EVENT_EFFECT_BUNDLE) 단위 상품과 효과 정의의 N:N 매핑을 다루는 계약. 매핑
 * Entity({@code CeremonyEffectDefinitionOption})는 효과 도메인에 있으므로 구현은
 * {@code feature.ceremony}가 제공한다.
 */
public interface UnitProductEffectPort {

    List<Long> findEffectDefinitionIds(Long unitProductId);

    /** 이 단위 상품에 효과 정의들을 연결한다. 비어 있으면 아무것도 하지 않는다. */
    void linkEffectDefinitions(UnitProduct unitProduct, List<Long> effectDefinitionIds);

    /** 이 단위 상품의 효과 연결을 통째로 교체한다(빈 목록이면 모두 해제). */
    void replaceEffectDefinitions(UnitProduct unitProduct, List<Long> effectDefinitionIds);
}
