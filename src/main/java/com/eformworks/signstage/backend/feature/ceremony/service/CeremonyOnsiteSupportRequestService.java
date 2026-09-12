package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyOnsiteSupportRequestDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOnsiteSupportRequest;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.OnsiteSupportRequestStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyOnsiteSupportRequestRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOnsiteSupportRequestDto;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현장지원 요청(관리자 견적) 협상 — signstage-docs
 * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절
 * (2026-09-12). 이 코드베이스에 없던 "요청 → 관리자가 값을 매김 → 요청자가 수락/거부"
 * 협상 패턴을 새로 도입한다. {@code CeremonyInquiryService}와 같이 파트너 쪽과 관리자 쪽
 * 메서드를 한 서비스에 같이 둔다 — 조직/행사 접근 검사는 {@code CeremonyService}의
 * package-private 헬퍼를 재사용한다.
 *
 * <p>쓰기 권한: 파트너 쪽은 신규 권한키 없이 기존 {@code ACTION_CEREMONY_MANAGE}를 재사용
 * (2.1/9장 결정 #6과 같은 원칙), 관리자 쪽은 신규 {@code ACTION_ONSITE_SUPPORT_REQUEST_MANAGE}
 * (PLATFORM_OPS 이상, 가격을 매기는 판단이라 조회보다 높은 등급).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyOnsiteSupportRequestService {

    private static final String ACTION_ONSITE_SUPPORT_REQUEST_MANAGE = "ACTION_ONSITE_SUPPORT_REQUEST_MANAGE";

    private final CeremonyOnsiteSupportRequestRepository ceremonyOnsiteSupportRequestRepository;
    private final CeremonyUnitProductPurchaseRepository ceremonyUnitProductPurchaseRepository;
    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final UnitProductRepository unitProductRepository;
    private final CeremonyService ceremonyService;
    private final UserRepository userRepository;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    // ──────────────────────────── 파트너 쪽 ────────────────────────────

    @Transactional
    public CeremonyOnsiteSupportRequestDto.Response.RequestSummary createRequest(
            Long organizationId, Long ceremonyId, Long currentUserId,
            CeremonyOnsiteSupportRequestDto.Request.CreateRequest request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        CeremonyOnsiteSupportRequest onsiteSupportRequest = CeremonyOnsiteSupportRequest.builder()
                .ceremony(ceremony)
                .requestedAt(request.getRequestedAt())
                .location(request.getLocation())
                .requesterNote(request.getRequesterNote())
                .build();
        ceremonyOnsiteSupportRequestRepository.save(onsiteSupportRequest);

        return toSummary(onsiteSupportRequest);
    }

    public List<CeremonyOnsiteSupportRequestDto.Response.RequestSummary> findRequests(
            Long organizationId, Long ceremonyId, Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyOnsiteSupportRequestRepository.findAllByCeremonyIdOrderByCreatedAtDesc(ceremonyId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public CeremonyOnsiteSupportRequestDto.Response.RequestSummary acceptRequest(
            Long organizationId, Long ceremonyId, Long requestId, Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        CeremonyOnsiteSupportRequest onsiteSupportRequest = findRequestInCeremonyOrThrow(ceremonyId, requestId);
        if (onsiteSupportRequest.getStatus() != OnsiteSupportRequestStatus.QUOTED) {
            throw new ApplicationException(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_NOT_QUOTED);
        }

        CeremonyUnitProductPurchase purchase = createPurchaseFromQuote(ceremony, onsiteSupportRequest);
        onsiteSupportRequest.accept(purchase.getId());

        return toSummary(onsiteSupportRequest);
    }

    @Transactional
    public CeremonyOnsiteSupportRequestDto.Response.RequestSummary declineRequest(
            Long organizationId, Long ceremonyId, Long requestId, Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        CeremonyOnsiteSupportRequest onsiteSupportRequest = findRequestInCeremonyOrThrow(ceremonyId, requestId);
        if (onsiteSupportRequest.getStatus() != OnsiteSupportRequestStatus.QUOTED) {
            throw new ApplicationException(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_NOT_QUOTED);
        }
        onsiteSupportRequest.decline();

        return toSummary(onsiteSupportRequest);
    }

    /**
     * 수락된 견적을 그대로 반영하는 평범한 구매 1건을 만든다 — 2.2절 발견(구매 원장은 카테고리와
     * 무관하게 전부 "플랫폼 이용료"로 집계됨)에 따라, 이 구매가 생기는 순간부터는 자가-체크아웃
     * 구매와 완전히 동일하게 취급된다(플랫폼 이용료 합계·구매 이력·"파트너사 구매 내역"·구매
     * 취소 전부 코드 변경 없이 그대로 작동). {@code purchase.approve()}(기존 메서드)를 그대로
     * 재사용해 견적을 매긴 관리자를 {@code reviewedBy}로 남긴다.
     */
    private CeremonyUnitProductPurchase createPurchaseFromQuote(Ceremony ceremony, CeremonyOnsiteSupportRequest onsiteSupportRequest) {
        UnitProduct anchor = unitProductRepository.findFirstByTypeOrderByIdAsc(UnitProductType.ONSITE_SUPPORT_REQUEST)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_ANCHOR_NOT_FOUND));
        LocalDate asOfDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));
        UnitProductPricePeriod period = ceremonyService.resolveSellableUnitProductPeriod(anchor, asOfDate);

        CeremonyUnitProductPurchase purchase = CeremonyUnitProductPurchase.builder().ceremony(ceremony).build();
        ceremonyUnitProductPurchaseRepository.save(purchase);

        ceremonyUnitProductPurchaseLineRepository.save(
                CeremonyUnitProductPurchaseLine.builder()
                        .purchase(purchase)
                        .unitProduct(anchor)
                        .quantity(1)
                        .currencyCode(ceremony.getCurrencyCode())
                        .purchasedName(anchor.getName())
                        .purchasedSalePrice(onsiteSupportRequest.getQuotedAmount())
                        .purchasedTaxCode(period.getPriceInfo().getTaxCode())
                        .build()
        );
        purchase.approve(onsiteSupportRequest.getQuotedBy());

        return purchase;
    }

    // ──────────────────────────── 플랫폼 관리자 쪽 ────────────────────────────

    /**
     * 조직 横단 목록 — organizationId/ceremonyId/status/requesterKeyword 전부 선택 필터다
     * ({@code CeremonyService#findUnitProductPurchaseRequests}와 같은 패턴). 조회는
     * PLATFORM_SUPPORT 이상이면 누구나(SecurityConfig가 이미 게이트).
     */
    public Page<PlatformAdminOnsiteSupportRequestDto.Response.RequestSummary> findRequestsAcrossOrganizations(
            OnsiteSupportRequestStatus status, Long organizationId, Long ceremonyId, String requesterKeyword, Pageable pageable
    ) {
        return ceremonyOnsiteSupportRequestRepository.search(status, organizationId, ceremonyId, requesterKeyword, pageable)
                .map(this::toAdminSummary);
    }

    @Transactional
    public PlatformAdminOnsiteSupportRequestDto.Response.RequestSummary quoteRequest(
            Long requestId, String actingPlatformRole, Long adminUserId, PlatformAdminOnsiteSupportRequestDto.Request.Quote request
    ) {
        checkAllowed(actingPlatformRole);

        CeremonyOnsiteSupportRequest onsiteSupportRequest = ceremonyOnsiteSupportRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_NOT_FOUND));
        if (onsiteSupportRequest.getStatus() != OnsiteSupportRequestStatus.REQUESTED) {
            throw new ApplicationException(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_NOT_REQUESTED);
        }
        onsiteSupportRequest.quote(request.getQuotedAmount(), request.getQuotedNote(), adminUserId);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.QUOTE_ONSITE_SUPPORT_REQUEST, null,
                onsiteSupportRequest.getCeremony().getOrganization().getId(),
                "onsiteSupportRequestId=" + requestId + ", quotedAmount=" + request.getQuotedAmount()
        );

        return toAdminSummary(onsiteSupportRequest);
    }

    // ──────────────────────────── 공용 ────────────────────────────

    private CeremonyOnsiteSupportRequest findRequestInCeremonyOrThrow(Long ceremonyId, Long requestId) {
        CeremonyOnsiteSupportRequest onsiteSupportRequest = ceremonyOnsiteSupportRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_NOT_FOUND));
        if (!onsiteSupportRequest.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.ONSITE_SUPPORT_REQUEST_NOT_FOUND);
        }
        return onsiteSupportRequest;
    }

    private void checkAllowed(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, ACTION_ONSITE_SUPPORT_REQUEST_MANAGE)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private CeremonyOnsiteSupportRequestDto.Response.RequestSummary toSummary(CeremonyOnsiteSupportRequest onsiteSupportRequest) {
        return new CeremonyOnsiteSupportRequestDto.Response.RequestSummary(
                onsiteSupportRequest.getId(),
                onsiteSupportRequest.getCeremony().getId(),
                onsiteSupportRequest.getRequestedAt(),
                onsiteSupportRequest.getLocation(),
                onsiteSupportRequest.getRequesterNote(),
                onsiteSupportRequest.getStatus().name(),
                onsiteSupportRequest.getQuotedAmount(),
                onsiteSupportRequest.getQuotedNote(),
                onsiteSupportRequest.getQuotedAt(),
                onsiteSupportRequest.getRespondedAt(),
                onsiteSupportRequest.getCreatedAt()
        );
    }

    private PlatformAdminOnsiteSupportRequestDto.Response.RequestSummary toAdminSummary(CeremonyOnsiteSupportRequest onsiteSupportRequest) {
        User requester = resolveUser(onsiteSupportRequest.getCreatedBy());
        User quotedByUser = resolveUser(onsiteSupportRequest.getQuotedBy());
        return new PlatformAdminOnsiteSupportRequestDto.Response.RequestSummary(
                onsiteSupportRequest.getId(),
                onsiteSupportRequest.getCeremony().getOrganization().getId(),
                onsiteSupportRequest.getCeremony().getOrganization().getName(),
                onsiteSupportRequest.getCeremony().getId(),
                onsiteSupportRequest.getCeremony().getTitle(),
                requester == null ? null : requester.getLoginId(),
                requester == null ? null : requester.getName(),
                onsiteSupportRequest.getRequestedAt(),
                onsiteSupportRequest.getLocation(),
                onsiteSupportRequest.getRequesterNote(),
                onsiteSupportRequest.getStatus().name(),
                onsiteSupportRequest.getQuotedAmount(),
                onsiteSupportRequest.getQuotedNote(),
                quotedByUser == null ? null : quotedByUser.getLoginId(),
                onsiteSupportRequest.getQuotedAt(),
                onsiteSupportRequest.getRespondedAt(),
                onsiteSupportRequest.getCreatedAt()
        );
    }

    private User resolveUser(Long userId) {
        return userId == null ? null : userRepository.findById(userId).orElse(null);
    }
}
