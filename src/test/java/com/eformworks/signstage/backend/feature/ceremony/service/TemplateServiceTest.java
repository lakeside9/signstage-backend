package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.model.StoredFile;
import com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link TemplateService#duplicateTemplate}의 서명란 복제 동작 단위 테스트 — legacy
 * (~/Works/eform/source/signstage/signstage-frontend) TemplateCloneServiceTest가 검증하던
 * "복제 시 서명란에 매핑된 signer 정보까지 그대로 복제된다"를 이 코드베이스 모델로 옮긴다.
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private TemplateFieldRepository templateFieldRepository;
    @Mock
    private CeremonyTemplateRepository ceremonyTemplateRepository;
    @Mock
    private DocumentStoragePort documentStoragePort;
    @Mock
    private CeremonyService ceremonyService;

    @InjectMocks
    private TemplateService templateService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CEREMONY_ID = 10L;
    private static final Long TEMPLATE_ID = 101L;
    private static final Long CURRENT_USER_ID = 1L;

    @Test
    @DisplayName("문서 복제 시 서명란에 매핑된 signer 정보까지 그대로 복제된다")
    void duplicateTemplate_clonesFieldsWithSignerMapping() throws IOException {
        // given
        Ceremony ceremony = Ceremony.builder().title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);

        Template original = Template.builder()
                .ceremony(ceremony)
                .title("원본 문서")
                .documentRole(TemplateDocumentRole.CONTRACT)
                .storageKey("templates/10/original.pdf")
                .originalFilename("original.pdf")
                .storedFilename("stored-original.pdf")
                .build();
        ReflectionTestUtils.setField(original, "id", TEMPLATE_ID);

        Signer signer = Signer.builder().ceremony(ceremony).name("서명자1").accessKey("signer-1").build();
        ReflectionTestUtils.setField(signer, "id", 1L);

        TemplateField originalField = TemplateField.builder()
                .template(original)
                .signer(signer)
                .fieldKey("field-1")
                .pageIndex(0)
                .fieldIndex(0)
                .fieldName("서명")
                .roleCode("서명자")
                .signOrder(1)
                .isRequired(true)
                .xRatio(new BigDecimal("0.10000"))
                .yRatio(new BigDecimal("0.20000"))
                .widthRatio(new BigDecimal("0.15000"))
                .heightRatio(new BigDecimal("0.05000"))
                .build();

        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID))
                .willReturn(Member.builder().role(MemberRole.OWNER).build());
        given(ceremonyService.calculateEffectiveCapacity(ceremony, CapacityType.TEMPLATES)).willReturn(10);
        given(templateRepository.countByCeremonyId(CEREMONY_ID)).willReturn(1L);
        given(templateRepository.findById(TEMPLATE_ID)).willReturn(Optional.of(original));

        Resource resource = mock(Resource.class);
        byte[] fileContent = "pdf-bytes".getBytes();
        given(resource.getContentAsByteArray()).willReturn(fileContent);
        given(documentStoragePort.loadAsResource(original.getStorageKey())).willReturn(resource);
        given(documentStoragePort.store(anyString(), anyString(), any(byte[].class)))
                .willReturn(new StoredFile("templates/10/duplicated.pdf", "stored-duplicated.pdf"));

        given(templateFieldRepository.findAllByTemplateId(TEMPLATE_ID)).willReturn(List.of(originalField));

        // when
        templateService.duplicateTemplate(ORGANIZATION_ID, CEREMONY_ID, TEMPLATE_ID, CURRENT_USER_ID);

        // then
        ArgumentCaptor<Template> templateCaptor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).save(templateCaptor.capture());
        Template duplicated = templateCaptor.getValue();
        assertThat(duplicated.getTitle()).isEqualTo("원본 문서 (복제)");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TemplateField>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        verify(templateFieldRepository).saveAll(fieldsCaptor.capture());

        List<TemplateField> clonedFields = fieldsCaptor.getValue();
        assertThat(clonedFields).hasSize(1);
        TemplateField clonedField = clonedFields.get(0);
        assertThat(clonedField.getTemplate()).isSameAs(duplicated);
        assertThat(clonedField.getSigner()).isSameAs(signer);
        assertThat(clonedField.getFieldKey()).isEqualTo(originalField.getFieldKey());
        assertThat(clonedField.getPageIndex()).isEqualTo(originalField.getPageIndex());
        assertThat(clonedField.getFieldIndex()).isEqualTo(originalField.getFieldIndex());
        assertThat(clonedField.getRoleCode()).isEqualTo(originalField.getRoleCode());
        assertThat(clonedField.getSignOrder()).isEqualTo(originalField.getSignOrder());
        assertThat(clonedField.getIsRequired()).isEqualTo(originalField.getIsRequired());
        assertThat(clonedField.getXRatio()).isEqualByComparingTo(originalField.getXRatio());
        assertThat(clonedField.getYRatio()).isEqualByComparingTo(originalField.getYRatio());
        assertThat(clonedField.getWidthRatio()).isEqualByComparingTo(originalField.getWidthRatio());
        assertThat(clonedField.getHeightRatio()).isEqualByComparingTo(originalField.getHeightRatio());
    }
}
