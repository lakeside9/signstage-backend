package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.feature.ceremony.dto.*;
import com.eformworks.signstage.backend.feature.ceremony.entity.*;
import com.eformworks.signstage.backend.feature.ceremony.repository.*;
import com.eformworks.signstage.backend.core.error.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AvailabilityTest {
    @Test
    void disabledSignerCanBeRestoredWithoutChangingAccessKey() {
        Ceremony ceremony = Ceremony.builder().title("ceremony").build();
        ReflectionTestUtils.setField(ceremony, "id", 1L);
        Signer signer = Signer.builder().ceremony(ceremony).name("signer").accessKey("key").build();
        ReflectionTestUtils.setField(signer, "id", 2L);
        CeremonyService access = mock(CeremonyService.class);
        SignerRepository repo = mock(SignerRepository.class);
        when(access.findCeremonyInOrganizationOrThrow(1L, 1L)).thenReturn(ceremony);
        when(repo.findById(2L)).thenReturn(Optional.of(signer));
        SignerService service = new SignerService(repo, mock(TemplateFieldRepository.class), mock(StrokeDataRepository.class),
                mock(CeremonyEventLogRepository.class), mock(CeremonyTemplateRepository.class), access);
        assertThat(service.updateEnabled(1L, 1L, 2L, 3L, new UpdateEnabledRequest(false)).isEnabled()).isFalse();
        assertThat(service.updateEnabled(1L, 1L, 2L, 3L, new UpdateEnabledRequest(true)).isEnabled()).isTrue();
        assertThat(signer.getAccessKey()).isEqualTo("key");
        verify(access, times(2)).checkCeremonyManageAccess(eq(ceremony), any(), eq(3L));
    }

    @Test
    void bulkSaveRetainsDisabledSignerButRejectsAdditionalPlacementBeforeDelete() {
        Ceremony ceremony = Ceremony.builder().title("ceremony").build();
        ReflectionTestUtils.setField(ceremony, "id", 1L);
        Template template = Template.builder().ceremony(ceremony).build();
        ReflectionTestUtils.setField(template, "id", 2L);
        Signer signer = Signer.builder().ceremony(ceremony).name("signer").build();
        ReflectionTestUtils.setField(signer, "id", 3L);
        signer.updateEnabled(false);
        TemplateFieldRepository fields = mock(TemplateFieldRepository.class);
        TemplateRepository templates = mock(TemplateRepository.class);
        SignerService signers = mock(SignerService.class);
        TemplateFieldService service = new TemplateFieldService(fields, templates, mock(CeremonyTemplateRepository.class),
                mock(CeremonyService.class), signers);
        when(templates.findById(2L)).thenReturn(Optional.of(template));
        when(signers.findSignerInCeremonyOrThrow(1L, 3L)).thenReturn(signer);
        when(fields.findAllByTemplateId(2L)).thenReturn(List.of(TemplateField.builder().template(template).signer(signer).build()));
        var first = new TemplateFieldDto.Request.CreateTemplateField(); first.setSignerId(3L); first.setFieldIndex(0);
        service.setFields(1L, 1L, 2L, 4L, new TemplateFieldDto.Request.SetFields(List.of(first)));
        verify(fields).deleteAllByTemplateId(2L);
        clearInvocations(fields);
        var extra = new TemplateFieldDto.Request.CreateTemplateField(); extra.setSignerId(3L); extra.setFieldIndex(1);
        assertThatThrownBy(() -> service.setFields(1L, 1L, 2L, 4L,
                new TemplateFieldDto.Request.SetFields(List.of(first, extra)))).isInstanceOf(ApplicationException.class);
        verify(fields, never()).deleteAllByTemplateId(anyLong());
    }
}
