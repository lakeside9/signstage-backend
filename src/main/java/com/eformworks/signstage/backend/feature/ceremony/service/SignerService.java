package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.SignerDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.SignerRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.StrokeDataRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서명자(Signer). {@code Ceremony} 직속이라 같은 행사의 TEST/MAIN 하위 행사가 명단을
 * 공유한다(signstage-docs business/ceremony-feature-migration-review.md 4.3절).
 * 조직/행사 접근 검사와 유효 한도 계산은 {@link CeremonyService}의 package-private
 * 헬퍼를 재사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignerService {

    private final SignerRepository signerRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final StrokeDataRepository strokeDataRepository;
    private final CeremonyEventLogRepository ceremonyEventLogRepository;
    private final CeremonyService ceremonyService;

    @Transactional
    public SignerDto.Response.SignerSummary createSigner(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            SignerDto.Request.CreateSigner request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        int effectiveLimit = ceremonyService.calculateEffectiveCapacity(ceremony, CapacityType.SIGNERS);
        long currentCount = signerRepository.countByCeremonyId(ceremonyId);
        if (currentCount >= effectiveLimit) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_SIGNER_LIMIT_EXCEEDED);
        }

        Signer signer = Signer.builder()
                .ceremony(ceremony)
                .name(request.getName())
                .position(request.getPosition())
                .affiliation(request.getAffiliation())
                .roleCode(request.getRoleCode())
                .accessKey(generateUniqueAccessKey())
                .build();
        signerRepository.save(signer);

        return toSummary(signer);
    }

    public List<SignerDto.Response.SignerSummary> findSigners(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return signerRepository.findAllByCeremonyId(ceremonyId).stream().map(this::toSummary).toList();
    }

    public SignerDto.Response.SignerSummary retrieveSigner(
            Long organizationId,
            Long ceremonyId,
            Long signerId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return toSummary(findSignerInCeremonyOrThrow(ceremonyId, signerId));
    }

    /** 이름/직책/소속/역할코드만 바꾼다. accessKey는 여기서 바꾸지 않는다. */
    @Transactional
    public SignerDto.Response.SignerSummary updateSigner(
            Long organizationId,
            Long ceremonyId,
            Long signerId,
            Long currentUserId,
            SignerDto.Request.UpdateSigner request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Signer signer = findSignerInCeremonyOrThrow(ceremonyId, signerId);
        signer.updateInfo(request.getName(), request.getPosition(), request.getAffiliation(), request.getRoleCode());
        return toSummary(signer);
    }

    /**
     * 서명란에 배정돼 있거나(template_fields), 실제로 서명한 기록(stroke_data)/감사 로그
     * (ceremony_event_logs)가 있는 서명자는 삭제할 수 없다 — 셋 중 하나라도 있으면
     * {@code SIGNER_IN_USE}. 아무 흔적도 없어야 지울 수 있다.
     */
    @Transactional
    public void deleteSigner(Long organizationId, Long ceremonyId, Long signerId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Signer signer = findSignerInCeremonyOrThrow(ceremonyId, signerId);
        boolean inUse = templateFieldRepository.existsBySignerId(signerId)
                || strokeDataRepository.existsBySignerId(signerId)
                || ceremonyEventLogRepository.existsByTargetSignerId(signerId);
        if (inUse) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_IN_USE);
        }

        signerRepository.delete(signer);
    }

    /** {@link com.eformworks.signstage.backend.feature.ceremony.service.TemplateFieldService}가 signerId 검증에 재사용한다. */
    Signer findSignerInCeremonyOrThrow(Long ceremonyId, Long signerId) {
        Signer signer = signerRepository.findById(signerId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.SIGNER_NOT_FOUND));
        if (!signer.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_NOT_FOUND);
        }
        return signer;
    }

    private String generateUniqueAccessKey() {
        String accessKey;
        do {
            accessKey = UUID.randomUUID().toString().replace("-", "");
        } while (signerRepository.existsByAccessKey(accessKey));
        return accessKey;
    }

    private SignerDto.Response.SignerSummary toSummary(Signer signer) {
        return new SignerDto.Response.SignerSummary(
                signer.getId(),
                signer.getCeremony().getId(),
                signer.getName(),
                signer.getPosition(),
                signer.getAffiliation(),
                signer.getRoleCode(),
                signer.getAccessKey(),
                signer.getCreatedAt()
        );
    }
}
