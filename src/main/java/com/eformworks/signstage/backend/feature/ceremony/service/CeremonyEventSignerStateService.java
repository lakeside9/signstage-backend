package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventSignerState;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.SignatureStatus;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventSignerStateRepository;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 이벤트 안에서 서명자별 "지금" 서명 상태를 투영·조회하는 단일 창구 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-STATE. {@code SignerPortalService}/
 * {@code CeremonyEventService}가 각자 {@code ceremony_event_logs}를 최신순으로 다시 훑어
 * "완료 여부"를 판정하던 것(서로 거의 같은 코드가 두 군데 있었다)을 이 서비스 하나로 대체한다.
 *
 * <p><b>upsert 안전성</b>: {@code (event_id, signer_id)}가 PK라 같은 서명자에 대한 동시 첫 호출이
 * 겹치면 뒤늦은 {@code save()}가 {@link DataIntegrityViolationException}으로 실패할 수 있다.
 * 이 프로젝트에는 원자적 upsert(native `INSERT ... ON DUPLICATE KEY UPDATE`) 관례가 없어(다른
 * repository도 전부 JPA 파생/JPQL 쿼리만 쓴다), 대신 실패하면 그 사이 다른 트랜잭션이 만든 행을
 * 다시 읽어 갱신하는 재시도 한 번으로 처리한다 — 한 서명자가 같은 순간 두 기기에서 동시에
 * 처음 상태를 만드는 경우는 실사용에서 사실상 없고(한 사람이 한 태블릿을 쓴다), 그래도 발생하면
 * 요청이 실패하지 않고 정상적으로 갱신까지 끝난다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyEventSignerStateService {

    private final CeremonyEventSignerStateRepository ceremonyEventSignerStateRepository;

    /** SIGNATURE_COMPLETE — {@code completionLogId}는 그 감사 로그 id(추적용, PRE-04). */
    @Transactional
    public void markCompleted(CeremonyEvent event, Signer signer, Long completionLogId) {
        findOrCreate(event, signer).markCompleted(completionLogId);
    }

    /** SIGNATURE_REPLACE(관리자, 단건) — "다시 서명하게" 초기화한다. */
    @Transactional
    public void markSigning(CeremonyEvent event, Signer signer) {
        findOrCreate(event, signer).markSigning();
    }

    /** SIGNATURE_CLEAR(서명자 본인, 서명란 하나) — 완전히 처음 상태로 되돌린다. */
    @Transactional
    public void markPending(CeremonyEvent event, Signer signer) {
        findOrCreate(event, signer).markPending();
    }

    /** 관리자 일괄 초기화(SIGNATURE_REPLACE, bulk-reset) — 개별 SIGNING과 달리 전부 PENDING으로 되돌린다. */
    @Transactional
    public void markAllPending(CeremonyEvent event, Collection<Signer> signers) {
        for (Signer signer : signers) {
            findOrCreate(event, signer).markPending();
        }
    }

    public boolean isSignerComplete(Long eventId, Long signerId) {
        return ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(eventId, signerId)
                .map(state -> state.getSignatureStatus() == SignatureStatus.COMPLETED)
                .orElse(false);
    }

    public boolean isAllComplete(Long eventId, Collection<Long> requiredSignerIds) {
        return requiredSignerIds.stream().allMatch(signerId -> isSignerComplete(eventId, signerId));
    }

    private CeremonyEventSignerState findOrCreate(CeremonyEvent event, Signer signer) {
        return ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(event.getId(), signer.getId())
                .orElseGet(() -> createOrRecover(event, signer));
    }

    private CeremonyEventSignerState createOrRecover(CeremonyEvent event, Signer signer) {
        try {
            return ceremonyEventSignerStateRepository.save(
                    CeremonyEventSignerState.builder().event(event).signer(signer).build()
            );
        } catch (DataIntegrityViolationException e) {
            // 동시에 다른 트랜잭션이 먼저 같은 (event, signer) 행을 만들었다 — 그 행을 다시 읽어
            // 갱신을 이어간다(클래스 문서 "upsert 안전성" 참고).
            return ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(event.getId(), signer.getId())
                    .orElseThrow(() -> e);
        }
    }
}
