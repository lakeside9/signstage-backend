package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.DemoScenarioDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.SignerRepository;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 체험 계정(VIEWER)의 시나리오 목록 — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 4.2/5.4절 결정(2026-09-10 구현).
 * 조직 스코프를 URL 파라미터로 받지 않는다 — 호출자(JWT의 userId)가 소속된 데모 조직을
 * 스스로 찾는다(데모 체험 계정은 데모 조직 하나에만 VIEWER로 속한다는 전제, 4.1절).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemoScenarioService {

    private final MemberRepository memberRepository;
    private final CeremonyEventRepository ceremonyEventRepository;
    private final SignerRepository signerRepository;

    public List<DemoScenarioDto.Response.DemoScenario> findScenarios(Long currentUserId) {
        Organization demoOrganization = resolveDemoOrganizationOrThrow(currentUserId);

        return ceremonyEventRepository
                .findAllByCeremony_Organization_IdAndStatusOrderByCreatedAtDesc(demoOrganization.getId(), CeremonyEventStatus.STARTED)
                .stream()
                .map(this::toScenario)
                .filter(Objects::nonNull)
                .toList();
    }

    private Organization resolveDemoOrganizationOrThrow(Long currentUserId) {
        return memberRepository.findAllByUserIdAndStatus(currentUserId, MemberStatus.ACTIVE).stream()
                .filter(member -> member.getOrganization().isDemo() && member.getRole() == MemberRole.VIEWER)
                .map(member -> member.getOrganization())
                .findFirst()
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
    }

    /**
     * 이벤트에 등록된 첫 서명자(표시 순서 기준)를 "그" 데모 서명자로 쓴다 — 데모 콘텐츠는
     * 서명자 1명으로 등록하는 걸 전제한다(5.1절). 서명자가 아직 없으면(콘텐츠 준비 중) 그
     * 이벤트는 목록에서 조용히 제외한다.
     */
    private DemoScenarioDto.Response.DemoScenario toScenario(CeremonyEvent event) {
        List<Signer> signers = signerRepository.findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(event.getCeremony().getId());
        if (signers.isEmpty()) {
            return null;
        }
        return new DemoScenarioDto.Response.DemoScenario(
                event.getId(), event.getCeremony().getTitle(), event.getAccessKey(), signers.get(0).getAccessKey()
        );
    }
}
