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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서명자 포털. JWT를 쓰지 않는다 — {@code eventAccessKey}/{@code signerAccessKey} 소지만으로
 * 접근한다(signstage-docs business/ceremony-feature-migration-review.md 2.3/4.5절 결정).
 * 조직 스코프 검사가 필요 없는 완전히 다른 인가 모델이라 {@link CeremonyService}의 헬퍼를
 * 재사용하지 않고 리포지토리를 직접 쓴다 — 다만 {@link CeremonyEventService#isAllRequiredSignersComplete}는
 * 조직 스코프 검사가 없는 순수 조회 헬퍼라 예외적으로 재사용한다({@link #completeSignature} 참고).
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
    private final CeremonyEventService ceremonyEventService;
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
                context.event().getEventType().name(),
                context.event().getStatus().name(),
                context.signer().getId(),
                context.signer().getName(),
                context.signer().getPosition(),
                context.signer().getAffiliation(),
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

        if (context.event().getStatus() != CeremonyEventStatus.STARTED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_NOT_IN_PROGRESS);
        }

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
     *
     * <p>격리 수준을 {@code READ_COMMITTED}로 명시한다 — 기본(REPEATABLE READ, MySQL) 그대로
     * 두면 이 트랜잭션이 이미 확립한 스냅샷 때문에, 아래에서 {@code findByIdForUpdate}로 잠금을
     * 잡은 뒤에도 "전원 완료" 판정 쿼리(ceremony_event_logs 조회)가 그 시점 이후 다른
     * 트랜잭션이 커밋한 내용을 못 볼 수 있다. READ_COMMITTED면 잠금 획득 이후의 모든 조회가
     * 항상 그 시점의 최신 커밋 데이터를 보므로, 아래 잠금과 조합하면 동시에 마지막 두 서명자가
     * 완료해도 폭죽 브로드캐스트가 정확히 한 번만 나간다(signstage-docs
     * business/ceremony-feature-migration-review.md 8.8절 참고).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void completeSignature(String eventAccessKey, String signerAccessKey) {
        PortalContext context = resolvePortalContext(eventAccessKey, signerAccessKey);

        if (context.event().getStatus() != CeremonyEventStatus.STARTED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_NOT_IN_PROGRESS);
        }

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

        // 폭죽(ALL_SIGNED_FIREWORKS) — 이 이벤트 행에 먼저 잠금을 잡아 동시 완료 요청들을
        // 이 구간에서 직렬화한 뒤(클래스 문서 주석 참고), "방금 전원 완료로 전환됐는가"를
        // 판정한다. 옵션이 적용됐는지는 여기서 검사하지 않는다 — 적용 여부는 프로젝터가
        // ProjectorContext.appliedOptionalFeatureCodes로 스스로 걸러서 소비하고, 다른 화면은
        // 이 메시지 타입을 아예 처리하지 않아 무시한다(다른 SIGNATURE_* 브로드캐스트와 같은
        // "사실은 항상 보내고 화면이 알아서 거른다" 원칙).
        CeremonyEvent lockedEvent = ceremonyEventRepository.findByIdForUpdate(context.event().getId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.PORTAL_EVENT_NOT_FOUND));
        if (ceremonyEventService.isAllRequiredSignersComplete(lockedEvent)) {
            ceremonyRealtimeNotifier.notifyAllSignersCompleted(lockedEvent.getId());
        }
    }

    /**
     * SIGNATURE_CLEAR — 서명 진행 중(STARTED)인 서명자가 자기 서명란 하나를 지우고 다시
     * 그린다. legacy(~/Works/eform/source/signstage/signstage-backend)
     * {@code SignerPortalController#replaceAndCompleteEventSignature}는 이미 완료했는지를
     * 아예 검사하지 않고 매번 무조건 교체+재완료 로그를 남긴다 — 행사가 끝나기 전까지는
     * 서명자 본인이 몇 번이든 다시 서명할 수 있다는 뜻이다. 이 프로젝트도 같은 규칙을
     * 따른다: 이벤트가 STARTED인 동안은 이미 완료했어도 self-serve로 지우고 다시 그릴 수
     * 있다(예전엔 완료 후엔 관리자의 REPLACE를 거쳐야 했는데, 그 제약을 없앴다). FINISHED
     * 이후엔 아래 상태 검사로 막힌다.
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
     * {@code SIGNATURE_COMPLETE}/{@code SIGNATURE_REPLACE}/{@code SIGNATURE_CLEAR} 중 이
     * 서명자의 가장 최근 로그가 {@code SIGNATURE_COMPLETE}인지로 "지금 완료 상태인가"를
     * 판정한다 — 단순 {@code existsBy(...SIGNATURE_COMPLETE)}는 REPLACE/CLEAR 이후에도 예전
     * 완료 로그가 남아있어 "완료 취소"를 반영하지 못한다(레거시가 못 고친 결함). CLEAR를
     * 목록에 넣은 건 {@link #clearFieldStroke}가 완료 후에도 self-serve로 지울 수 있게
     * 바뀌면서다 — 안 넣으면 "완료 → 지움 → 다시 그림 → 완료 재요청"에서 마지막 완료 재요청이
     * "이미 완료됨"으로 잘못 막힌다(지운 사실이 최신 판정에 반영되지 않아서).
     */
    private boolean isSignerSignatureComplete(Long eventId, Long signerId) {
        return ceremonyEventLogRepository
                .findTopByCeremonyEventIdAndTargetSignerIdAndEventActionInOrderByCreatedAtDesc(
                        eventId,
                        signerId,
                        List.of(CeremonyEventAction.SIGNATURE_COMPLETE, CeremonyEventAction.SIGNATURE_REPLACE, CeremonyEventAction.SIGNATURE_CLEAR)
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

    /**
     * 이 서명자가 실제로 서명해야 하는 필수 서명란 — CONTRACT 문서의 것만 모은다.
     *
     * <p>EXHIBITION도 같은 서명자를 필수로 요구하도록 매핑돼 있지만({@code checkSignerMappingConsistency}
     * 참고, READY 전이 조건), 그건 화면 배치 일관성(전시용 화면에 이 서명자 자리가 있어야
     * 실시간 서명 궤적을 보여줄 수 있다)을 위한 것이지 이 서명자가 그 서명란에도 직접 서명해야
     * 한다는 뜻이 아니다 — 서명자 포털은 CONTRACT만 보여주고 서명도 CONTRACT에만 받는다
     * (legacy {@code SignerView.tsx}와 같다). EXHIBITION 화면에 뜨는 서명은 같은 서명자의
     * CONTRACT 획을 그대로 재사용해 그린다({@code MappedDocumentPreview}/{@code ProjectorView}의
     * signerId 폴백). 예전에는 이 메서드가 EXHIBITION의 필수 서명란까지 다 모았는데, 포털이
     * 그 서명란엔 애초에 서명을 제출할 방법을 주지 않으니 완료 조건을 영원히 못 채워
     * "서명했는데도 행사 종료 버튼이 안 켜지는" 버그로 이어졌다.
     */
    private List<TemplateField> collectRequiredFieldsForSigner(CeremonyEvent event, Signer signer) {
        List<CeremonyTemplate> mappings = ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.CONTRACT);
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
