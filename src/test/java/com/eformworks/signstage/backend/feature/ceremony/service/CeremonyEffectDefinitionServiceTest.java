package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEffectDefinitionDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCode;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyEffectDefinitionService} 단위 테스트 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-CATALOG-04.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyEffectDefinitionServiceTest {

    @Mock
    private CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;
    @Mock
    private OptionalFeatureRepository optionalFeatureRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    @Mock
    private RolePermissionService rolePermissionService;

    private CeremonyEffectDefinitionService ceremonyEffectDefinitionService;

    @BeforeEach
    void setUp() {
        ceremonyEffectDefinitionService = new CeremonyEffectDefinitionService(
                ceremonyEffectDefinitionRepository,
                optionalFeatureRepository,
                platformAdminAuditLogRecorder,
                rolePermissionService
        );
        lenient().when(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_EFFECT_MANAGE")).thenReturn(true);
    }

    private OptionalFeature signerFieldZoom() {
        OptionalFeature feature = OptionalFeature.builder()
                .code(OptionalFeatureCode.SIGNER_FIELD_ZOOM)
                .name("서명 하이라이트")
                .currencyCode("KRW")
                .supplyPrice(BigDecimal.TEN)
                .salePrice(BigDecimal.TEN)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.ZERO)
                .build();
        ReflectionTestUtils.setField(feature, "id", 10L);
        return feature;
    }

    private CeremonyEffectDefinitionDto.Request.CreateCeremonyEffectDefinition createRequest(String code) {
        return new CeremonyEffectDefinitionDto.Request.CreateCeremonyEffectDefinition(
                code, "PROJECTOR", "SIGNATURE_COMPLETED", 10L,
                "하이라이트", "설명", "projector-signature-highlight", false, null
        );
    }

    @Test
    @DisplayName("PLATFORM_OPS 미만 등급은 정의를 등록할 수 없다")
    void createDefinition_insufficientRole_fail() {
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_EFFECT_MANAGE")).willReturn(false);

        assertThatThrownBy(() -> ceremonyEffectDefinitionService.createDefinition(
                "PLATFORM_SUPPORT", 1L, createRequest("HIGHLIGHT")
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
        verify(ceremonyEffectDefinitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 등록된 code면 거부된다")
    void createDefinition_duplicateCode_fail() {
        given(ceremonyEffectDefinitionRepository.existsByCode("HIGHLIGHT")).willReturn(true);

        assertThatThrownBy(() -> ceremonyEffectDefinitionService.createDefinition(
                "PLATFORM_OPS", 1L, createRequest("HIGHLIGHT")
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_DEFINITION_CODE_DUPLICATE);
    }

    @Test
    @DisplayName("잘못된 target/trigger 값은 거부된다")
    void createDefinition_invalidClassification_fail() {
        CeremonyEffectDefinitionDto.Request.CreateCeremonyEffectDefinition request =
                new CeremonyEffectDefinitionDto.Request.CreateCeremonyEffectDefinition(
                        "HIGHLIGHT", "SCREEN", "SIGNATURE_COMPLETED", 10L,
                        "하이라이트", null, "renderer", false, null
                );

        assertThatThrownBy(() -> ceremonyEffectDefinitionService.createDefinition("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("존재하지 않는 필수 선택옵션을 참조하면 거부된다")
    void createDefinition_requiredOptionalFeatureNotFound_fail() {
        given(optionalFeatureRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ceremonyEffectDefinitionService.createDefinition(
                "PLATFORM_OPS", 1L, createRequest("HIGHLIGHT")
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 분류(target, trigger) 안에 기존 정의가 있으면 10 단위로 뒤에 배치된다")
    void createDefinition_appendsAtEndOfGroupWithStep10() {
        OptionalFeature feature = signerFieldZoom();
        given(optionalFeatureRepository.findById(10L)).willReturn(Optional.of(feature));
        CeremonyEffectDefinition existing = CeremonyEffectDefinition.builder()
                .code("HIGHLIGHT").targetType(CeremonyEffectTarget.PROJECTOR)
                .triggerType(CeremonyEffectTrigger.SIGNATURE_COMPLETED)
                .requiredOptionalFeature(feature).displayName("하이라이트").rendererKey("r").displayOrder(10)
                .build();
        given(ceremonyEffectDefinitionRepository.findAllByGroupForUpdate(
                CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.SIGNATURE_COMPLETED
        )).willReturn(List.of(existing));

        ceremonyEffectDefinitionService.createDefinition("PLATFORM_OPS", 1L, createRequest("PULSE"));

        ArgumentCaptor<CeremonyEffectDefinition> captor = ArgumentCaptor.forClass(CeremonyEffectDefinition.class);
        verify(ceremonyEffectDefinitionRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(20);
    }

    @Test
    @DisplayName("configJson은 JSON object로 정규화되어 저장·조회된다")
    void createDefinition_configJson_roundTrips() {
        OptionalFeature feature = signerFieldZoom();
        given(optionalFeatureRepository.findById(10L)).willReturn(Optional.of(feature));
        given(ceremonyEffectDefinitionRepository.findAllByGroupForUpdate(any(), any())).willReturn(List.of());

        CeremonyEffectDefinitionDto.Request.CreateCeremonyEffectDefinition request =
                new CeremonyEffectDefinitionDto.Request.CreateCeremonyEffectDefinition(
                        "HIGHLIGHT", "PROJECTOR", "SIGNATURE_COMPLETED", 10L,
                        "하이라이트", null, "renderer", false, Map.of("color", "blue")
                );

        CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary response =
                ceremonyEffectDefinitionService.createDefinition("PLATFORM_OPS", 1L, request);

        assertThat(response.getConfigJson()).containsEntry("color", "blue");
    }

    @Test
    @DisplayName("수정해도 code/target/trigger/rendererKey/requiredOptionalFeature는 바뀌지 않는다")
    void updateDefinition_immutableFieldsUnchanged() {
        OptionalFeature feature = signerFieldZoom();
        CeremonyEffectDefinition definition = CeremonyEffectDefinition.builder()
                .code("HIGHLIGHT").targetType(CeremonyEffectTarget.PROJECTOR)
                .triggerType(CeremonyEffectTrigger.SIGNATURE_COMPLETED)
                .requiredOptionalFeature(feature).displayName("하이라이트").rendererKey("projector-signature-highlight")
                .displayOrder(10)
                .build();
        ReflectionTestUtils.setField(definition, "id", 1L);
        given(ceremonyEffectDefinitionRepository.findById(1L)).willReturn(Optional.of(definition));

        CeremonyEffectDefinitionDto.Request.UpdateCeremonyEffectDefinition request =
                new CeremonyEffectDefinitionDto.Request.UpdateCeremonyEffectDefinition(
                        "새 표시명", "새 설명", false, false, true, null
                );

        CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary response =
                ceremonyEffectDefinitionService.updateDefinition(1L, "PLATFORM_OPS", 1L, request);

        assertThat(response.getCode()).isEqualTo("HIGHLIGHT");
        assertThat(response.getTargetType()).isEqualTo("PROJECTOR");
        assertThat(response.getTriggerType()).isEqualTo("SIGNATURE_COMPLETED");
        assertThat(response.getRendererKey()).isEqualTo("projector-signature-highlight");
        assertThat(response.getRequiredOptionalFeatureId()).isEqualTo(10L);
        assertThat(response.getDisplayName()).isEqualTo("새 표시명");
        assertThat(response.getEnabled()).isFalse();
        assertThat(response.getUserVisible()).isFalse();
        assertThat(response.getManuallyTriggerable()).isTrue();
    }

    @Test
    @DisplayName("공개 카탈로그는 활성+사용자노출 정의만 담긴 repository 결과를 그대로 전달한다")
    void findPublicDefinitions_delegatesToActiveAndUserVisibleQuery() {
        CeremonyEffectDefinition visible = definitionWithId(1L, 10);
        given(ceremonyEffectDefinitionRepository
                .findAllByEnabledTrueAndUserVisibleTrueOrderByTargetTypeAscTriggerTypeAscDisplayOrderAsc())
                .willReturn(List.of(visible));

        List<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> response =
                ceremonyEffectDefinitionService.findPublicDefinitions();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(1L);
        // 비활성/숨김 필터 자체는 이 derived query가 DB에서 수행한다 — 서비스는 결과를
        // 그대로 전달할 뿐이라 여기서는 delegation만 검증한다.
    }

    @Test
    @DisplayName("순서 이동 요청이 그룹 id 전체와 다르면 거부된다")
    void reorderDefinitions_groupMismatch_fail() {
        CeremonyEffectDefinition a = definitionWithId(1L, 10);
        CeremonyEffectDefinition b = definitionWithId(2L, 20);
        given(ceremonyEffectDefinitionRepository.findAllByGroupForUpdate(
                CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.SIGNATURE_COMPLETED
        )).willReturn(List.of(a, b));

        CeremonyEffectDefinitionDto.Request.ReorderCeremonyEffectDefinitions request =
                new CeremonyEffectDefinitionDto.Request.ReorderCeremonyEffectDefinitions(
                        "PROJECTOR", "SIGNATURE_COMPLETED", List.of(1L)
                );

        assertThatThrownBy(() -> ceremonyEffectDefinitionService.reorderDefinitions("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_DEFINITION_ORDER_GROUP_MISMATCH);
    }

    @Test
    @DisplayName("순서 이동은 나열 순서대로 10 단위로 재정규화한다")
    void reorderDefinitions_renormalizesByStep10() {
        CeremonyEffectDefinition a = definitionWithId(1L, 10);
        CeremonyEffectDefinition b = definitionWithId(2L, 20);
        CeremonyEffectDefinition c = definitionWithId(3L, 30);
        given(ceremonyEffectDefinitionRepository.findAllByGroupForUpdate(
                CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.SIGNATURE_COMPLETED
        )).willReturn(List.of(a, b, c));

        CeremonyEffectDefinitionDto.Request.ReorderCeremonyEffectDefinitions request =
                new CeremonyEffectDefinitionDto.Request.ReorderCeremonyEffectDefinitions(
                        "PROJECTOR", "SIGNATURE_COMPLETED", List.of(3L, 1L, 2L)
                );

        List<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> response =
                ceremonyEffectDefinitionService.reorderDefinitions("PLATFORM_OPS", 1L, request);

        assertThat(response).extracting(CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary::getId)
                .containsExactly(3L, 1L, 2L);
        assertThat(response).extracting(CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary::getDisplayOrder)
                .containsExactly(10, 20, 30);
    }

    private CeremonyEffectDefinition definitionWithId(Long id, int displayOrder) {
        OptionalFeature feature = signerFieldZoom();
        CeremonyEffectDefinition definition = CeremonyEffectDefinition.builder()
                .code("CODE_" + id).targetType(CeremonyEffectTarget.PROJECTOR)
                .triggerType(CeremonyEffectTrigger.SIGNATURE_COMPLETED)
                .requiredOptionalFeature(feature).displayName("d" + id).rendererKey("r").displayOrder(displayOrder)
                .build();
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }
}
