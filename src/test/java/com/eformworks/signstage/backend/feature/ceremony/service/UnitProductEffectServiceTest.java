package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinitionOption;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link UnitProductEffectService} 단위 테스트 — 이벤트 효과 묶음 단위 상품과 효과 정의의 매핑 저장.
 * 2026-11-11 feature.billing 분리 전에는 {@code UnitProductServiceTest}에 있던 시나리오다.
 */
@ExtendWith(MockitoExtension.class)
class UnitProductEffectServiceTest {

    @Mock
    private CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    @Mock
    private CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;

    @InjectMocks
    private UnitProductEffectService unitProductEffectService;

    private static final Long UNIT_PRODUCT_ID = 901L;

    private UnitProduct bundle() {
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", UNIT_PRODUCT_ID);
        return bundle;
    }

    private CeremonyEffectDefinition effectDefinition(Long id) {
        return effectDefinition(id, CeremonyEffectTrigger.SIGNATURE_COMPLETED);
    }

    private CeremonyEffectDefinition effectDefinition(Long id, CeremonyEffectTrigger triggerType) {
        CeremonyEffectDefinition definition = CeremonyEffectDefinition.builder()
                .code("EFFECT_" + id)
                .targetType(CeremonyEffectTarget.PROJECTOR)
                .triggerType(triggerType)
                .displayName("효과 " + id)
                .rendererKey("renderer")
                .displayOrder(0)
                .build();
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }

    @Test
    @DisplayName("연결 — 지정한 효과마다 매핑 행을 만든다")
    void linkEffectDefinitions_savesOptionPerDefinition() {
        given(ceremonyEffectDefinitionRepository.findAllById(List.of(10L, 20L)))
                .willReturn(List.of(effectDefinition(10L), effectDefinition(20L)));

        unitProductEffectService.linkEffectDefinitions(bundle(), List.of(10L, 20L));

        verify(ceremonyEffectDefinitionOptionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("연결 — 개별 서명 확인 효과와 전체 서명 완료 효과를 한 묶음에 섞으면 거부한다")
    void linkEffectDefinitions_rejectsMixedTriggers() {
        given(ceremonyEffectDefinitionRepository.findAllById(List.of(10L, 20L))).willReturn(List.of(
                effectDefinition(10L, CeremonyEffectTrigger.SIGNATURE_COMPLETED),
                effectDefinition(20L, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED)
        ));

        assertThatThrownBy(() -> unitProductEffectService.linkEffectDefinitions(bundle(), List.of(10L, 20L)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_BUNDLE_TRIGGER_MIXED);
        verify(ceremonyEffectDefinitionOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("연결 — 빈 목록이면 아무것도 만들지 않는다")
    void linkEffectDefinitions_emptyList_doesNothing() {
        unitProductEffectService.linkEffectDefinitions(bundle(), List.of());

        verify(ceremonyEffectDefinitionRepository, never()).findAllById(any());
        verify(ceremonyEffectDefinitionOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("연결 — 존재하지 않는 effectDefinitionId가 섞여 있으면 EFFECT_DEFINITION_NOT_FOUND")
    void linkEffectDefinitions_throwsWhenEffectDefinitionNotFound() {
        given(ceremonyEffectDefinitionRepository.findAllById(List.of(10L))).willReturn(List.of());

        assertThatThrownBy(() -> unitProductEffectService.linkEffectDefinitions(bundle(), List.of(10L)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND);
    }

    @Test
    @DisplayName("연결 — 같은 effectDefinitionId가 중복되면 INVALID_REQUEST")
    void linkEffectDefinitions_rejectsDuplicateIds() {
        assertThatThrownBy(() -> unitProductEffectService.linkEffectDefinitions(bundle(), List.of(10L, 10L)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 회귀 방지(2026-09-10) — deleteAllByUnitProductId는 파생 delete 쿼리라 DELETE SQL을 즉시
     * 내보내지 않으므로, 다시 저장하기 전에 flush해야 유니크 제약(uq_cedo_definition_product)
     * 위반이 나지 않는다. Mockito로는 실제 flush 타이밍을 재현할 수 없으므로 호출 순서만 검증한다.
     */
    @Test
    @DisplayName("교체 — delete → flush → save 순서로 통째로 교체한다")
    void replaceEffectDefinitions_deletesFlushesThenSaves() {
        given(ceremonyEffectDefinitionRepository.findAllById(List.of(10L))).willReturn(List.of(effectDefinition(10L)));

        unitProductEffectService.replaceEffectDefinitions(bundle(), List.of(10L));

        InOrder order = inOrder(ceremonyEffectDefinitionOptionRepository);
        order.verify(ceremonyEffectDefinitionOptionRepository).deleteAllByUnitProductId(UNIT_PRODUCT_ID);
        order.verify(ceremonyEffectDefinitionOptionRepository).flush();
        order.verify(ceremonyEffectDefinitionOptionRepository).save(any());
    }

    @Test
    @DisplayName("교체 — 빈 목록이면 전부 해제하고 새로 만들지 않는다")
    void replaceEffectDefinitions_emptyList_clearsAll() {
        unitProductEffectService.replaceEffectDefinitions(bundle(), List.of());

        verify(ceremonyEffectDefinitionOptionRepository).deleteAllByUnitProductId(UNIT_PRODUCT_ID);
        verify(ceremonyEffectDefinitionOptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("조회 — 연결된 효과 정의 id 목록을 돌려준다")
    void findEffectDefinitionIds_returnsLinkedIds() {
        UnitProduct bundle = bundle();
        given(ceremonyEffectDefinitionOptionRepository.findAllByUnitProductId(UNIT_PRODUCT_ID)).willReturn(List.of(
                CeremonyEffectDefinitionOption.builder().effectDefinition(effectDefinition(10L)).unitProduct(bundle).build(),
                CeremonyEffectDefinitionOption.builder().effectDefinition(effectDefinition(20L)).unitProduct(bundle).build()
        ));

        assertThat(unitProductEffectService.findEffectDefinitionIds(UNIT_PRODUCT_ID)).containsExactly(10L, 20L);
    }
}
