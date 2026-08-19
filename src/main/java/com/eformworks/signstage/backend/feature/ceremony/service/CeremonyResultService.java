package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyResultDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.ActorType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyResult;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyResultType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyTemplate;
import com.eformworks.signstage.backend.feature.ceremony.entity.StrokeData;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.model.FieldStrokes;
import com.eformworks.signstage.backend.feature.ceremony.model.StoredFile;
import com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 결과 PDF(CeremonyResult). {@code FINISHED} 상태의 하위 행사에서만, 이벤트당 1회
 * 생성한다. 렌더링은 {@link SignatureOverlayRenderer}(PDFBox 직접 그리기,
 * signstage-docs business/ceremony-feature-migration-review.md §5.1 결정)가 한다.
 *
 * <p>매핑된 문서(CONTRACT/EXHIBITION)마다 결과 PDF를 하나씩 만드는데, 서명자 포털은
 * CONTRACT에만 서명을 받으므로 EXHIBITION 문서의 필드는 원래 자기 자신의 스트로크가 없다 —
 * {@link #buildStrokeContext}/{@link #buildFieldStrokes}가 화면(행사제어/전시용 화면)과 같은
 * signerId 폴백으로 같은 서명자의 CONTRACT 획을 재사용해 그린다. 안 그러면 계약서 PDF엔
 * 서명이 있는데 전시용 PDF만 서명란이 빈 채로 나온다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyResultService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CeremonyResultRepository ceremonyResultRepository;
    private final CeremonyEventRepository ceremonyEventRepository;
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
        // 이 체크는 방금 여기서 완료로 전이시키는 호출 자체를 막지 않는다 — Ceremony는 이 시점엔
        // 아직 IN_PROGRESS이고, 완료 전이는 결과 생성이 전부 성공한 뒤에만 일어난다.
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = ceremonyEventService.findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.FINISHED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_NOT_FINISHED);
        }
        if (ceremonyResultRepository.existsByCeremonyEventId(eventId)) {
            throw new ApplicationException(CeremonyErrorCode.RESULTS_ALREADY_GENERATED);
        }

        StrokeContext strokeContext = buildStrokeContext(event);
        List<CeremonyResult> results = new ArrayList<>();
        for (CeremonyTemplate mapping : ceremonyTemplateRepository.findAllByCeremonyEventId(eventId)) {
            results.add(generateResultForTemplate(event, mapping, strokeContext));
        }

        ceremonyEventLogRepository.save(
                CeremonyEventLog.builder()
                        .ceremonyEvent(event)
                        .actorType(ActorType.ADMIN)
                        .actorId(currentUserId)
                        .eventAction(CeremonyEventAction.GENERATE_RESULTS)
                        .build()
        );

        completeCeremonyIfAllMainEventsFinished(ceremony, event);

        return results.stream().map(this::toSummary).toList();
    }

    /**
     * 이 Ceremony 아래 만들어진 본행사(MAIN)가 전부 FINISHED + 결과 생성까지 끝났으면 Ceremony를
     * COMPLETED로 전이한다. 방금 결과를 생성한 {@code justCompletedEvent}는 DB 재조회 없이
     * "결과 있음"으로 간주한다(같은 트랜잭션에서 방금 만들었으므로). 본행사가 하나도 없으면
     * 당연히 미완료다.
     */
    private void completeCeremonyIfAllMainEventsFinished(Ceremony ceremony, CeremonyEvent justCompletedEvent) {
        if (justCompletedEvent.getEventType() != CeremonyEventType.MAIN
                || ceremony.getStatus() != CeremonyStatus.IN_PROGRESS) {
            return;
        }

        List<CeremonyEvent> mainEvents =
                ceremonyEventRepository.findAllByCeremonyIdAndEventType(ceremony.getId(), CeremonyEventType.MAIN);
        if (mainEvents.isEmpty()) {
            return;
        }

        for (CeremonyEvent mainEvent : mainEvents) {
            boolean hasResults = mainEvent.getId().equals(justCompletedEvent.getId())
                    || ceremonyResultRepository.existsByCeremonyEventId(mainEvent.getId());
            if (mainEvent.getStatus() != CeremonyEventStatus.FINISHED || !hasResults) {
                return;
            }
        }

        ceremony.changeStatus(CeremonyStatus.COMPLETED);
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

    private CeremonyResult generateResultForTemplate(CeremonyEvent event, CeremonyTemplate mapping, StrokeContext strokeContext) {
        Template template = mapping.getTemplate();
        List<FieldStrokes> fieldStrokesList = templateFieldRepository.findAllByTemplateId(template.getId()).stream()
                .map(field -> buildFieldStrokes(field, strokeContext))
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
     * 이벤트 전체 획을 한 번만 읽어 (1) 필드별 획 목록(저장 순서)과 (2) 서명자별 "실제로
     * 그린 원본 서명란"을 미리 계산해 둔다 — {@link #buildFieldStrokes}가 문서마다/필드마다
     * 반복 조회하지 않도록. 원본 서명란은 그 서명자의 가장 이른(created_at) 획이 속한
     * templateFieldId로 정한다 — 서명자 포털이 실제로 서명을 받는 문서(CONTRACT)가 사실상
     * 언제나 여기 해당한다.
     */
    private StrokeContext buildStrokeContext(CeremonyEvent event) {
        List<StrokeData> allStrokes = strokeDataRepository.findAllByCeremonyEventId(event.getId());

        Map<Long, List<StrokeData>> strokesByField = allStrokes.stream()
                .collect(Collectors.groupingBy(stroke -> stroke.getTemplateField().getId()));
        strokesByField.values().forEach(list -> list.sort(Comparator.comparing(StrokeData::getStrokeSeq)));

        Map<Long, Long> originFieldBySignerId = new LinkedHashMap<>();
        allStrokes.stream()
                .sorted(Comparator.comparing(StrokeData::getCreatedAt))
                .forEach(stroke -> originFieldBySignerId.putIfAbsent(stroke.getSigner().getId(), stroke.getTemplateField().getId()));

        return new StrokeContext(strokesByField, originFieldBySignerId);
    }

    /**
     * 이 필드의 획을 저장 순서(strokeSeq)대로 모은다. {@code rawData}는 필드 바운딩 박스 기준
     * 0~1 비율 점 배열({@code [[x1,y1],[x2,y2],...]}, 좌상단 원점)이라는 계약이다(이번
     * 라운드에서 처음 정함).
     *
     * <p>이 필드 자신에게 직접 그려진 획이 없으면(전시용 문서는 서명자 포털이 서명을 받지
     * 않으므로 거의 항상 이 경우다) 같은 서명자가 다른 매핑 문서(대개 CONTRACT)에 그린 획을
     * 대신 쓴다 — 화면(행사제어/전시용 화면, {@code MappedDocumentPreview}/{@code ProjectorView})이
     * 이미 쓰고 있는 signerId 폴백과 같은 방식이다. 이게 없으면 계약서 PDF엔 서명이 있는데
     * 전시용 PDF엔 서명란이 빈 채로 나온다.
     */
    private FieldStrokes buildFieldStrokes(TemplateField field, StrokeContext strokeContext) {
        List<StrokeData> strokeRows = strokeContext.strokesByField().getOrDefault(field.getId(), List.of());
        if (strokeRows.isEmpty() && field.getSigner() != null) {
            Long originFieldId = strokeContext.originFieldBySignerId().get(field.getSigner().getId());
            if (originFieldId != null) {
                strokeRows = strokeContext.strokesByField().getOrDefault(originFieldId, List.of());
            }
        }

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

    private record StrokeContext(Map<Long, List<StrokeData>> strokesByField, Map<Long, Long> originFieldBySignerId) {
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
