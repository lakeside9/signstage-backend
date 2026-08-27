package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.ProjectorDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.StrokeDataDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateFieldDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyTemplate;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.StrokeData;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.StrokeDataRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 프로젝터 화면(전시용 화면, `/projector/:eventAccessKey`). JWT를 쓰지 않는다 —
 * {@code eventAccessKey} 소지만으로 접근한다({@link SignerPortalService}와 같은 인가 모델).
 * 조직 스코프 검사가 필요 없어 {@link CeremonyService}의 헬퍼를 재사용하지 않고 리포지토리를
 * 직접 쓴다 — {@link SignerPortalService}와 같은 기존 관례.
 *
 * <p>PDF 페이지 렌더링은 {@link TemplateService#buildTemplateInfo(Template)}/
 * {@link TemplateService#renderPage(Template, int, float)}(package-private)를 그대로
 * 재사용한다 — JWT 경로와 accessKey 경로가 "이미 조회한 Template으로 렌더링한다"는 핵심
 * 로직은 완전히 같고, 앞단의 인가 방식만 다르기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectorService {

    private final CeremonyEventRepository ceremonyEventRepository;
    private final CeremonyTemplateRepository ceremonyTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final StrokeDataRepository strokeDataRepository;
    private final CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    private final TemplateService templateService;

    public ProjectorDto.Response.ProjectorContext retrieveContext(String eventAccessKey) {
        CeremonyEvent event = resolveEvent(eventAccessKey);
        Template exhibitionTemplate = findExhibitionTemplate(event).orElse(null);

        ProjectorDto.Response.ExhibitionDocument exhibition = null;
        if (exhibitionTemplate != null) {
            TemplateDto.Response.TemplateInfo info = templateService.buildTemplateInfo(exhibitionTemplate);
            List<TemplateField> fields = templateFieldRepository.findAllByTemplateId(exhibitionTemplate.getId());

            List<TemplateFieldDto.Response.TemplateFieldSummary> fieldSummaries = fields.stream()
                    .map(this::toFieldSummary)
                    .toList();

            Map<Long, String> signerNamesById = new LinkedHashMap<>();
            for (TemplateField field : fields) {
                Signer signer = field.getSigner();
                if (signer != null) {
                    signerNamesById.putIfAbsent(signer.getId(), signer.getName());
                }
            }
            List<ProjectorDto.Response.SignerInfo> signerInfos = signerNamesById.entrySet().stream()
                    .map(entry -> new ProjectorDto.Response.SignerInfo(entry.getKey(), entry.getValue()))
                    .toList();

            exhibition = new ProjectorDto.Response.ExhibitionDocument(
                    exhibitionTemplate.getId(),
                    exhibitionTemplate.getTitle(),
                    info.getPageCount(),
                    info.getWidth(),
                    info.getHeight(),
                    fieldSummaries,
                    signerInfos
            );
        }

        List<String> appliedOptionalFeatureCodes = ceremonyEventOptionalFeatureRepository
                .findAllByCeremonyEventId(event.getId())
                .stream()
                .map(applied -> applied.getOptionalFeature().getCode().name())
                .toList();

        return new ProjectorDto.Response.ProjectorContext(
                event.getId(), event.getName(), event.getEventType().name(), event.getStatus().name(),
                event.getAccessKey(), exhibition, appliedOptionalFeatureCodes
        );
    }

    public byte[] renderPage(String eventAccessKey, int pageIndex, float scale) {
        CeremonyEvent event = resolveEvent(eventAccessKey);
        Template exhibitionTemplate = findExhibitionTemplate(event)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_IN_CEREMONY));
        return templateService.renderPage(exhibitionTemplate, pageIndex, scale);
    }

    public List<StrokeDataDto.Response.StrokeSummary> findStrokes(String eventAccessKey) {
        CeremonyEvent event = resolveEvent(eventAccessKey);
        return strokeDataRepository.findAllByCeremonyEventId(event.getId()).stream()
                .map(this::toStrokeSummary)
                .toList();
    }

    private CeremonyEvent resolveEvent(String eventAccessKey) {
        return ceremonyEventRepository.findByAccessKey(eventAccessKey)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.PORTAL_EVENT_NOT_FOUND));
    }

    private java.util.Optional<Template> findExhibitionTemplate(CeremonyEvent event) {
        return ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.EXHIBITION)
                .stream()
                .findFirst()
                .map(CeremonyTemplate::getTemplate);
    }

    private TemplateFieldDto.Response.TemplateFieldSummary toFieldSummary(TemplateField field) {
        return new TemplateFieldDto.Response.TemplateFieldSummary(
                field.getId(),
                field.getTemplate().getId(),
                field.getSigner() != null ? field.getSigner().getId() : null,
                field.getFieldKey(),
                field.getPageIndex(),
                field.getFieldIndex(),
                field.getFieldName(),
                field.getRoleCode(),
                field.getSignOrder(),
                field.getIsRequired(),
                field.getXRatio(),
                field.getYRatio(),
                field.getWidthRatio(),
                field.getHeightRatio(),
                field.getCreatedAt()
        );
    }

    private StrokeDataDto.Response.StrokeSummary toStrokeSummary(StrokeData stroke) {
        return new StrokeDataDto.Response.StrokeSummary(
                stroke.getId(),
                stroke.getSigner().getId(),
                stroke.getTemplateField().getId(),
                stroke.getStrokeSeq(),
                stroke.getRawData(),
                stroke.getCreatedAt()
        );
    }
}
