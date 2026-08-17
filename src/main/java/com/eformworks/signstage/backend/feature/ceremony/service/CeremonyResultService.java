package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyResultDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.ActorType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyResult;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyResultType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyTemplate;
import com.eformworks.signstage.backend.feature.ceremony.entity.StrokeData;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.model.FieldStrokes;
import com.eformworks.signstage.backend.feature.ceremony.model.StoredFile;
import com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyResultRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.StrokeDataRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.ceremony.support.SignatureOverlayRenderer;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.integration.storage.common.error.StorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 결과 PDF(CeremonyResult). {@code FINISHED} 상태의 하위 행사에서만, 이벤트당 1회
 * 생성한다. 렌더링은 {@link SignatureOverlayRenderer}(PDFBox 직접 그리기,
 * signstage-docs business/ceremony-feature-migration-review.md §5.1 결정)가 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyResultService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CeremonyResultRepository ceremonyResultRepository;
    private final CeremonyTemplateRepository ceremonyTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final StrokeDataRepository strokeDataRepository;
    private final CeremonyEventLogRepository ceremonyEventLogRepository;
    private final DocumentStoragePort documentStoragePort;
    private final CeremonyService ceremonyService;
    private final CeremonyEventService ceremonyEventService;

    @Transactional
    public List<CeremonyResultDto.Response.CeremonyResultSummary> generateResults(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        CeremonyEvent event = ceremonyEventService.findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.FINISHED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_NOT_FINISHED);
        }
        if (ceremonyResultRepository.existsByCeremonyEventId(eventId)) {
            throw new ApplicationException(CeremonyErrorCode.RESULTS_ALREADY_GENERATED);
        }

        List<CeremonyResult> results = new ArrayList<>();
        for (CeremonyTemplate mapping : ceremonyTemplateRepository.findAllByCeremonyEventId(eventId)) {
            results.add(generateResultForTemplate(event, mapping));
        }

        ceremonyEventLogRepository.save(
                CeremonyEventLog.builder()
                        .ceremonyEvent(event)
                        .actorType(ActorType.ADMIN)
                        .actorId(currentUserId)
                        .eventAction(CeremonyEventAction.GENERATE_RESULTS)
                        .build()
        );

        return results.stream().map(this::toSummary).toList();
    }

    public List<CeremonyResultDto.Response.CeremonyResultSummary> findResults(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        ceremonyEventService.findEventInCeremonyOrThrow(ceremonyId, eventId);
        return ceremonyResultRepository.findAllByCeremonyEventId(eventId).stream().map(this::toSummary).toList();
    }

    public DownloadedResult downloadResultFile(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long resultId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        ceremonyEventService.findEventInCeremonyOrThrow(ceremonyId, eventId);
        CeremonyResult result = ceremonyResultRepository.findById(resultId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.RESULT_NOT_FOUND));
        if (!result.getCeremonyEvent().getId().equals(eventId)) {
            throw new ApplicationException(CeremonyErrorCode.RESULT_NOT_FOUND);
        }

        Resource resource;
        try {
            resource = documentStoragePort.loadAsResource(result.getStorageKey());
        } catch (StorageException e) {
            throw new ApplicationException(CeremonyErrorCode.RESULT_GENERATION_FAILED, e);
        }
        return new DownloadedResult(resource, result.getOriginalFilename());
    }

    private CeremonyResult generateResultForTemplate(CeremonyEvent event, CeremonyTemplate mapping) {
        Template template = mapping.getTemplate();
        List<FieldStrokes> fieldStrokesList = templateFieldRepository.findAllByTemplateId(template.getId()).stream()
                .map(field -> buildFieldStrokes(event, field))
                .toList();

        byte[] originalBytes;
        byte[] renderedBytes;
        try {
            Resource originalResource = documentStoragePort.loadAsResource(template.getStorageKey());
            originalBytes = originalResource.getInputStream().readAllBytes();
            renderedBytes = SignatureOverlayRenderer.render(originalBytes, fieldStrokesList);
        } catch (IOException | StorageException e) {
            throw new ApplicationException(CeremonyErrorCode.RESULT_GENERATION_FAILED, e);
        }

        StoredFile storedFile = documentStoragePort.store(
                "results/" + event.getId(), template.getOriginalFilename(), renderedBytes
        );

        CeremonyResult result = CeremonyResult.builder()
                .ceremonyEvent(event)
                .template(template)
                .resultType(CeremonyResultType.valueOf(mapping.getDocumentRole().name()))
                .storageKey(storedFile.storageKey())
                .originalFilename(template.getOriginalFilename())
                .storedFilename(storedFile.storedFilename())
                .fileSize((long) renderedBytes.length)
                .checksum(sha256Hex(renderedBytes))
                .build();
        ceremonyResultRepository.save(result);
        return result;
    }

    /**
     * 이 필드의 획을 저장 순서(strokeSeq)대로 모은다. {@code rawData}는 필드 바운딩 박스 기준
     * 0~1 비율 점 배열({@code [[x1,y1],[x2,y2],...]}, 좌상단 원점)이라는 계약이다(이번
     * 라운드에서 처음 정함).
     */
    private FieldStrokes buildFieldStrokes(CeremonyEvent event, TemplateField field) {
        List<StrokeData> strokeRows = strokeDataRepository
                .findAllByCeremonyEventIdAndTemplateFieldIdOrderByStrokeSeq(event.getId(), field.getId());

        List<List<double[]>> strokes = strokeRows.stream()
                .map(stroke -> parseStroke(stroke.getRawData()))
                .toList();

        return new FieldStrokes(
                field.getPageIndex(),
                field.getXRatio(),
                field.getYRatio(),
                field.getWidthRatio(),
                field.getHeightRatio(),
                strokes
        );
    }

    private List<double[]> parseStroke(String rawData) {
        try {
            double[][] points = OBJECT_MAPPER.readValue(rawData, double[][].class);
            return Arrays.asList(points);
        } catch (IOException e) {
            // 형식이 안 맞는 스트로크는 그리지 않고 건너뛴다 — 결과물 생성 전체를 막을 이유는 없다.
            return List.of();
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    private CeremonyResultDto.Response.CeremonyResultSummary toSummary(CeremonyResult result) {
        return new CeremonyResultDto.Response.CeremonyResultSummary(
                result.getId(),
                result.getCeremonyEvent().getId(),
                result.getTemplate().getId(),
                result.getResultType().name(),
                result.getOriginalFilename(),
                result.getFileSize(),
                result.getChecksum(),
                result.getCreatedAt()
        );
    }

    /** 컨트롤러가 {@code Content-Disposition} 헤더를 만드는 데 필요한 파일명을 함께 돌려준다. */
    public record DownloadedResult(Resource resource, String originalFilename) {
    }
}
