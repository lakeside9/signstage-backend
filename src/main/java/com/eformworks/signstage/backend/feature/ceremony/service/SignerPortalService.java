package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.SignerPortalDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.StrokeDataDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateFieldDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.ActorType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyTemplate;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.StrokeData;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.SignerRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.StrokeDataRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서명자 포털. JWT를 쓰지 않는다 — {@code eventAccessKey}/{@code signerAccessKey} 소지만으로
 * 접근한다(signstage-docs business/ceremony-feature-migration-review.md 2.3/4.5절 결정).
 * 조직 스코프 검사가 필요 없는 완전히 다른 인가 모델이라 {@link CeremonyService}의 헬퍼를
 * 재사용하지 않고 리포지토리를 직접 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignerPortalService {

    private final CeremonyEventRepository ceremonyEventRepository;
    private final SignerRepository signerRepository;
    private final CeremonyTemplateRepository ceremonyTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final StrokeDataRepository strokeDataRepository;
    private final CeremonyEventLogRepository ceremonyEventLogRepository;
    private final CeremonyRealtimeNotifier ceremonyRealtimeNotifier;
    private final TemplateService templateService;

    public SignerPortalDto.Response.PortalContext retrievePortalContext(String eventAccessKey, String signerAccessKey) {
        PortalContext context = resolvePortalContext(eventAccessKey, signerAccessKey);

        List<SignerPortalDto.Response.RequiredFieldStatus> fieldStatuses = collectRequiredFieldsForSigner(
                context.event(), context.signer()
        ).stream()
                .map(field -> new SignerPortalDto.Response.RequiredFieldStatus(
                        field.getId(),
                        field.getTemplate().getId(),
                        field.getFieldName(),
                        field.getPageIndex(),
                        strokeDataRepository.existsByCeremonyEventIdAndSignerIdAndTemplateFieldId(
                                context.event().getId(), context.signer().getId(), field.getId()
                        )
                ))
                .toList();

        return new SignerPortalDto.Response.PortalContext(
                context.event().getId(),
                context.event().getName(),
                context.event().getStatus().name(),
                context.signer().getId(),
                context.signer().getName(),
                fieldStatuses
        );
    }

    /**
     * legacy {@code SignerView.tsx}처럼 서명용(CONTRACT) 문서 전체를 배경으로 보여주기 위한
     * 문서 정보 + 전체 서명란 목록. CONTRACT 매핑이 없으면 {@code null}이다. 페이지 이미지는
     * {@link #renderContractPage}로 따로 받는다({@link ProjectorService}와 같은 분리).
     */
    public SignerPortalDto.Response.PortalContractDocument retrieveContract(String eventAccessKey, String signerAccessKey) {
        PortalContext context = resolvePortalContext(eventAccessKey, signerAccessKey);
        Template contractTemplate = findContractTemplate(context.event()).orElse(null);
        if (contractTemplate == null) {
            return null;
        }

        TemplateDto.Response.TemplateInfo info = templateService.buildTemplateInfo(contractTemplate);
        List<TemplateFieldDto.Response.TemplateFieldSummary> fields = templateFieldRepository
                .findAllByTemplateId(contractTemplate.getId())
                .stream()
                .map(this::toFieldSummary)
                .toList();

        return new SignerPortalDto.Response.PortalContractDocument(
                contractTemplate.getId(), contractTemplate.getTitle(), info.getPageCount(), info.getWidth(), info.getHeight(), fields
        );
    }

    public byte[] renderContractPage(String eventAccessKey, String signerAccessKey, int pageIndex, float scale) {
        PortalContext context = resolvePortalContext(eventAccessKey, signerAccessKey);
        Template contractTemplate = findContractTemplate(context.event())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_IN_CEREMONY));
        return templateService.renderPage(contractTemplate, pageIndex, scale);
    }

    /**
     * 이 서명자 본인의 획만이 아니라 이벤트 전체 획을 돌려준다 — legacy가 "같은 문서에 이미
     * 서명한 다른 사람들의 서명"도 함께 보여주는 것과 같다({@link ProjectorService#findStrokes}와
     * 같은 조회, 인가만 signerAccessKey 기준으로 다르다).
     */
    public List<StrokeDataDto.Response.StrokeSummary> findStrokes(String eventAccessKey, String signerAccessKey) {
        PortalContext context = resolvePortalContext(eventAccessKey, signerAccessKey);
        return strokeDataRepository.findAllByCeremonyEventId(context.event().getId()).stream()
                .map(this::toStrokeSummary)
                .toList();
    }

    private Optional<Template> findContractTemplate(CeremonyEvent event) {
        return ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.CONTRACT)
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

    @Transactional
    public SignerPortalDto.Response.StrokeSubmitted submitStroke(
            String eventAccessKey,
            String signerAccessKey,
            SignerPortalDto.Request.SubmitStroke request
    ) {
        PortalContext context = resolvePortalContext(eventAccessKey, signerAccessKey);

        TemplateField field = templateFieldRepository.findById(request.getTemplateFieldId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_FIELD_NOT_FOUND));

        checkFieldMappedToEvent(context.event(), field);
        if (field.getSigner() == null || !field.getSigner().getId().equals(context.signer().getId())) {
            throw new ApplicationException(CeremonyErrorCode.PORTAL_FIELD_NOT_ASSIGNED_TO_SIGNER);
        }

        StrokeData stroke = StrokeData.builder()
                .ceremonyEvent(context.event())
                .signer(context.signer())
                .templateField(field)
                .strokeSeq(request.getStrokeSeq())
                .rawData(request.getRawData())
                .build();
        strokeDataRepository.save(stroke);

        ceremonyRealtimeNotifier.notifyStrokeSubmitted(
                context.event().getId(), context.signer().getId(), field.getId(), stroke.getStrokeSeq(), stroke.getRawData()
        );

        return new SignerPortalDto.Response.StrokeSubmitted(
                stroke.getId(), field.getId(), stroke.getStrokeSeq(), stroke.getCreatedAt()
        );
    }

    /**
     * 배정된 모든 필수 서명란에 스트로크가 있어야 완료할 수 있다 — 레거시의 "로그 스캔으로
     * 완료 판정" 방식을 그대로 따른다(별도 completed 컬럼 없음).
     */
    @Transactional
    public void completeSignature(String eventAccessKey, String signerAccessKey) {
        PortalContext context = resolvePortalContext(eventAccessKey, signerAccessKey);

        List<TemplateField> requiredFields = collectRequiredFieldsForSigner(context.event(), context.signer());
        boolean allSigned = requiredFields.stream().allMatch(field ->
                strokeDataRepository.existsByCeremonyEventIdAndSignerIdAndTemplateFieldId(
                        context.event().getId(), context.signer().getId(), field.getId()
                ));
        if (!allSigned) {
            throw new ApplicationException(CeremonyErrorCode.SIGNATURE_INCOMPLETE);
        }

        if (isSignerSignatureComplete(context.event().getId(), context.signer().getId())) {
            throw new ApplicationException(CeremonyErrorCode.SIGNATURE_ALREADY_COMPLETED);
        }

        ceremonyEventLogRepository.save(
                CeremonyEventLog.builder()
                        .ceremonyEvent(context.event())
                        .actorType(ActorType.SIGNER)
                        .actorId(context.signer().getId())
                        .eventAction(CeremonyEventAction.SIGNATURE_COMPLETE)
                        .targetSigner(context.signer())
                        .build()
        );

        ceremonyRealtimeNotifier.notifySignatureCompleted(
                context.event().getId(), context.signer().getId(), context.signer().getName()
        );
    }

    /**
     * SIGNATURE_CLEAR — 서명 진행 중(STARTED)이고 아직 완료 전인 서명자가 자기 서명란 하나를
     * 지우고 다시 그린다. 이미 완료한 뒤에는 self-serve로 지울 수 없다 — 관리자의 REPLACE
     * ({@code CeremonyEventService.replaceSignerSignature})를 거쳐야 한다.
     */
    @Transactional
    public void clearFieldStroke(String eventAccessKey, String signerAccessKey, Long templateFieldId) {
        PortalContext context = resolvePortalContext(eventAccessKey, signerAccessKey);

        TemplateField field = templateFieldRepository.findById(templateFieldId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_FIELD_NOT_FOUND));
        checkFieldMappedToEvent(context.event(), field);
        if (field.getSigner() == null || !field.getSigner().getId().equals(context.signer().getId())) {
            throw new ApplicationException(CeremonyErrorCode.PORTAL_FIELD_NOT_ASSIGNED_TO_SIGNER);
        }

        if (context.event().getStatus() != CeremonyEventStatus.STARTED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_NOT_IN_PROGRESS);
        }
        if (isSignerSignatureComplete(context.event().getId(), context.signer().getId())) {
            throw new ApplicationException(CeremonyErrorCode.SIGNATURE_ALREADY_COMPLETED);
        }

        strokeDataRepository.deleteAllByCeremonyEventIdAndSignerIdAndTemplateFieldId(
                context.event().getId(), context.signer().getId(), field.getId()
        );

        ceremonyEventLogRepository.save(
                CeremonyEventLog.builder()
                        .ceremonyEvent(context.event())
                        .actorType(ActorType.SIGNER)
                        .actorId(context.signer().getId())
                        .eventAction(CeremonyEventAction.SIGNATURE_CLEAR)
                        .targetSigner(context.signer())
                        .message("templateFieldId=" + field.getId())
                        .build()
        );

        ceremonyRealtimeNotifier.notifySignatureCleared(context.event().getId(), context.signer().getId(), field.getId());
    }

    /**
     * {@code SIGNATURE_COMPLETE}/{@code SIGNATURE_REPLACE} 중 이 서명자의 가장 최근 로그가
     * {@code SIGNATURE_COMPLETE}인지로 "지금 완료 상태인가"를 판정한다 — 단순
     * {@code existsBy(...SIGNATURE_COMPLETE)}는 REPLACE 이후에도 예전 완료 로그가 남아있어
     * "완료 취소"를 반영하지 못한다(레거시가 못 고친 결함).
     */
    private boolean isSignerSignatureComplete(Long eventId, Long signerId) {
        return ceremonyEventLogRepository
                .findTopByCeremonyEventIdAndTargetSignerIdAndEventActionInOrderByCreatedAtDesc(
                        eventId,
                        signerId,
                        List.of(CeremonyEventAction.SIGNATURE_COMPLETE, CeremonyEventAction.SIGNATURE_REPLACE)
                )
                .map(log -> log.getEventAction() == CeremonyEventAction.SIGNATURE_COMPLETE)
                .orElse(false);
    }

    private PortalContext resolvePortalContext(String eventAccessKey, String signerAccessKey) {
        CeremonyEvent event = ceremonyEventRepository.findByAccessKey(eventAccessKey)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.PORTAL_EVENT_NOT_FOUND));
        Signer signer = signerRepository.findByAccessKey(signerAccessKey)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.PORTAL_SIGNER_NOT_FOUND));

        // Signer는 Ceremony 직속이라 이벤트와 같은 CeremonyEvent일 필요는 없다 — TEST/MAIN이
        // 명단을 공유한다(4.3절). 짝이 아니면 존재 노출을 최소화하기 위해 signer 쪽과 같은
        // 코드를 쓴다.
        if (!signer.getCeremony().getId().equals(event.getCeremony().getId())) {
            throw new ApplicationException(CeremonyErrorCode.PORTAL_SIGNER_NOT_FOUND);
        }
        return new PortalContext(event, signer);
    }

    private void checkFieldMappedToEvent(CeremonyEvent event, TemplateField field) {
        boolean mapped = ceremonyTemplateRepository
                .existsByCeremonyEventIdAndTemplateId(event.getId(), field.getTemplate().getId());
        if (!mapped) {
            throw new ApplicationException(CeremonyErrorCode.PORTAL_FIELD_NOT_MAPPED_TO_EVENT);
        }
    }

    /** 이 이벤트에 매핑된 템플릿들 중, 이 서명자에게 배정된 필수 서명란만 모은다. */
    private List<TemplateField> collectRequiredFieldsForSigner(CeremonyEvent event, Signer signer) {
        List<CeremonyTemplate> mappings = ceremonyTemplateRepository.findAllByCeremonyEventId(event.getId());
        List<TemplateField> result = new ArrayList<>();
        for (CeremonyTemplate mapping : mappings) {
            for (TemplateField field : templateFieldRepository.findAllByTemplateId(mapping.getTemplate().getId())) {
                boolean assignedToThisSigner = field.getSigner() != null && field.getSigner().getId().equals(signer.getId());
                if (Boolean.TRUE.equals(field.getIsRequired()) && assignedToThisSigner) {
                    result.add(field);
                }
            }
        }
        return result;
    }

    private record PortalContext(CeremonyEvent event, Signer signer) {
    }
}
