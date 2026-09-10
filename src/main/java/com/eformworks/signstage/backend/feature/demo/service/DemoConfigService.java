package com.eformworks.signstage.backend.feature.demo.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.SignerRepository;
import com.eformworks.signstage.backend.feature.demo.dto.DemoConfigDto;
import com.eformworks.signstage.backend.feature.demo.entity.DemoConfig;
import com.eformworks.signstage.backend.feature.demo.error.DemoErrorCode;
import com.eformworks.signstage.backend.feature.demo.repository.DemoConfigRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 체험형 데모 프로필 — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 13장 결정(2026-09-10). legacy
 * {@code feature.demo.service.DemoConfigService}와 같은 구조를 그대로 가져왔다(공개 조회
 * 메서드 2개는 legacy 데모 셸이 그대로 소비하는 계약이라 동작까지 동일해야 한다) — 관리자
 * 쪽만 이 프로젝트의 동적 RBAC/`platform-admin` 관례로 새로 설계했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemoConfigService {

    private final DemoConfigRepository demoConfigRepository;
    private final CeremonyEventRepository ceremonyEventRepository;
    private final SignerRepository signerRepository;
    private final RolePermissionService rolePermissionService;

    // ==================== 공개(legacy 데모 셸 계약) ====================

    /** slug가 없거나 프로필이 없거나 비활성/참조 행사가 데모 조직 소속이 아니면 null(에러 아님). */
    public DemoConfigDto.Response.Config getConfig(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        return demoConfigRepository.findBySlug(slug)
                .filter(DemoConfig::isEnabled)
                .flatMap(config -> resolvePublicDemoEvent(config.getEventAccessKey()).map(event -> toConfig(config)))
                .orElse(null);
    }

    /** 참조 행사를 찾을 수 없는 프로필(예: 행사가 삭제됨)은 조용히 목록에서 제외한다. */
    public List<DemoConfigDto.Response.PublicSummary> listPublicConfigs() {
        return demoConfigRepository.findAllByOrderBySlugAsc().stream()
                .filter(DemoConfig::isEnabled)
                .flatMap(config -> resolvePublicDemoEvent(config.getEventAccessKey())
                        .map(event -> toPublicSummary(config, event))
                        .stream())
                .toList();
    }

    // ==================== 플랫폼 관리자(이 프로젝트 관례) ====================

    public List<DemoConfigDto.Response.Config> findAll(String actingPlatformRole) {
        checkCanManage(actingPlatformRole);
        return demoConfigRepository.findAllByOrderBySlugAsc().stream().map(this::toConfig).toList();
    }

    public List<DemoConfigDto.Response.DemoEventOption> findEventOptions(String actingPlatformRole) {
        checkCanManage(actingPlatformRole);
        return ceremonyEventRepository.findAllByCeremony_Organization_DemoTrueOrderByCreatedAtDesc().stream()
                .map(this::toEventOption)
                .toList();
    }

    @Transactional
    public DemoConfigDto.Response.Config upsert(String actingPlatformRole, DemoConfigDto.Request.Upsert request) {
        checkCanManage(actingPlatformRole);

        CeremonyEvent event = resolvePublicDemoEvent(request.getEventAccessKey())
                .orElseThrow(() -> new ApplicationException(DemoErrorCode.EVENT_NOT_DEMO_EVENT));

        // 중복 지정 방어 + 요청에 넣은 순서를 그대로 유지(legacy와 같은 방식).
        List<String> validatedSignerAccessKeys = new LinkedHashSet<>(request.getSignerAccessKeys()).stream()
                .map(signerAccessKey -> {
                    Signer signer = signerRepository.findByAccessKey(signerAccessKey)
                            .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.SIGNER_NOT_FOUND));
                    if (!signer.getCeremony().getId().equals(event.getCeremony().getId())) {
                        throw new ApplicationException(CeremonyErrorCode.SIGNER_NOT_FOUND);
                    }
                    return signer.getAccessKey();
                })
                .toList();

        DemoConfig config = demoConfigRepository.findBySlug(request.getSlug())
                .orElseGet(() -> DemoConfig.builder().slug(request.getSlug()).enabled(true).build());
        config.update(
                event.getAccessKey(), validatedSignerAccessKeys, request.getPages(), request.getStartPage(),
                request.getSpacing(), request.getEnabled()
        );
        demoConfigRepository.save(config);

        return toConfig(config);
    }

    @Transactional
    public void delete(String actingPlatformRole, String slug) {
        checkCanManage(actingPlatformRole);
        DemoConfig config = demoConfigRepository.findBySlug(slug)
                .orElseThrow(() -> new ApplicationException(DemoErrorCode.CONFIG_NOT_FOUND));
        demoConfigRepository.delete(config);
    }

    // ==================== 내부 헬퍼 ====================

    private void checkCanManage(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_DEMO_CEREMONY_MANAGE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    /** 데모 조직(Organization.isDemo) 소속 행사만 데모 프로필이 가리킬 수 있다. */
    private Optional<CeremonyEvent> resolvePublicDemoEvent(String eventAccessKey) {
        return ceremonyEventRepository.findByAccessKey(eventAccessKey)
                .filter(event -> event.getCeremony().getOrganization().isDemo());
    }

    private DemoConfigDto.Response.Config toConfig(DemoConfig config) {
        return new DemoConfigDto.Response.Config(
                config.getSlug(),
                config.getEventAccessKey(),
                config.getSignerAccessKeys(),
                config.getPages(),
                config.getStartPage(),
                config.getSpacing(),
                config.isEnabled(),
                buildProjectorUrl(config),
                buildSignerUrls(config),
                config.getUpdatedAt()
        );
    }

    private DemoConfigDto.Response.PublicSummary toPublicSummary(DemoConfig config, CeremonyEvent event) {
        return new DemoConfigDto.Response.PublicSummary(
                config.getSlug(), config.isEnabled(), event.getName(), event.getCeremony().getTitle(),
                config.getSignerAccessKeys().size()
        );
    }

    private DemoConfigDto.Response.DemoEventOption toEventOption(CeremonyEvent event) {
        List<DemoConfigDto.Response.SignerOption> signers = signerRepository
                .findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(event.getCeremony().getId()).stream()
                .map(signer -> new DemoConfigDto.Response.SignerOption(
                        signer.getId(), signer.getAccessKey(), signer.getName(), signer.getAffiliation(), signer.getPosition()
                ))
                .toList();
        return new DemoConfigDto.Response.DemoEventOption(
                event.getId(), event.getAccessKey(), event.getName(), event.getCeremony().getTitle(), signers
        );
    }

    /** 이 프로젝트의 실제 라우트 모양(/projector/:eventAccessKey)으로 만든다 — legacy 라우트와 다르다. */
    private String buildProjectorUrl(DemoConfig config) {
        StringBuilder url = new StringBuilder("/projector/").append(config.getEventAccessKey()).append("?embed=1");
        if (config.getPages() != null) {
            url.append("&pages=").append(config.getPages());
        }
        if (config.getStartPage() != null) {
            url.append("&startPage=").append(config.getStartPage());
        }
        if (config.getSpacing() != null) {
            url.append("&spacing=").append(config.getSpacing());
        }
        return url.toString();
    }

    /** 이 프로젝트의 실제 라우트 모양(/portal/:eventAccessKey/:signerAccessKey)으로 만든다. */
    private List<String> buildSignerUrls(DemoConfig config) {
        return config.getSignerAccessKeys().stream()
                .filter(Objects::nonNull)
                .map(signerAccessKey -> "/portal/" + config.getEventAccessKey() + "/" + signerAccessKey + "?embed=1")
                .toList();
    }
}
