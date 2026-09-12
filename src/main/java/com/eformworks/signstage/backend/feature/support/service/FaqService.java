package com.eformworks.signstage.backend.feature.support.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.support.dto.FaqDto;
import com.eformworks.signstage.backend.feature.support.entity.Faq;
import com.eformworks.signstage.backend.feature.support.error.SupportErrorCode;
import com.eformworks.signstage.backend.feature.support.repository.FaqRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FAQ 관리 — signstage-docs business/partner-support-center-review.md 4장.
 * {@code CeremonyEffectDefinitionService}와 같은 패턴: 등록·수정·순서 이동은 플랫폼 관리자
 * 전용({@code ACTION_FAQ_MANAGE}), 활성 목록 조회는 인증된 사용자 누구나 가능하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private static final int DISPLAY_ORDER_STEP = 10;
    private static final String ACTION_FAQ_MANAGE = "ACTION_FAQ_MANAGE";

    private final FaqRepository faqRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    /** {@code /api/faqs} — 인증된 사용자 누구나, 활성 FAQ만 표시 순서대로. 카테고리별 그룹핑은 프런트가 한다. */
    public List<FaqDto.Response.FaqSummary> findPublicFaqs() {
        return faqRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    /** 관리자 상세/수정 화면용 단건 조회 — 활성 여부와 무관하게 조회할 수 있다. */
    public FaqDto.Response.FaqSummary findFaq(Long faqId) {
        return toSummary(faqRepository.findById(faqId)
                .orElseThrow(() -> new ApplicationException(SupportErrorCode.FAQ_NOT_FOUND)));
    }

    /** {@code keyword}는 category/question/answer 중 하나라도 포함하면 매칭된다(2026-09-12 사용자 요청). */
    public Page<FaqDto.Response.FaqSummary> findFaqs(String keyword, Boolean active, Pageable pageable) {
        return faqRepository.search(keyword, active, pageable).map(this::toSummary);
    }

    @Transactional
    public FaqDto.Response.FaqSummary createFaq(
            String actingPlatformRole, Long adminUserId, FaqDto.Request.CreateFaq request
    ) {
        checkAllowed(actingPlatformRole);

        int displayOrder = faqRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .mapToInt(Faq::getDisplayOrder)
                .max()
                .orElse(0) + DISPLAY_ORDER_STEP;

        Faq faq = Faq.builder()
                .category(request.getCategory())
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .displayOrder(displayOrder)
                .build();
        faqRepository.save(faq);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.CREATE_FAQ, null, null, "faqId=" + faq.getId()
        );
        return toSummary(faq);
    }

    @Transactional
    public FaqDto.Response.FaqSummary updateFaq(
            Long faqId, String actingPlatformRole, Long adminUserId, FaqDto.Request.UpdateFaq request
    ) {
        checkAllowed(actingPlatformRole);

        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new ApplicationException(SupportErrorCode.FAQ_NOT_FOUND));
        faq.updateInfo(request.getCategory(), request.getQuestion(), request.getAnswer(), request.getActive());

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_FAQ, null, null, "faqId=" + faqId
        );
        return toSummary(faq);
    }

    @Transactional
    public void deleteFaq(Long faqId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole);

        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new ApplicationException(SupportErrorCode.FAQ_NOT_FOUND));
        faqRepository.delete(faq);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.DELETE_FAQ, null, null, "faqId=" + faqId
        );
    }

    /**
     * 전체 FAQ 목록을 원하는 순서로 통째로 재인덱싱한다({@code UnitProductService#updateDisplayOrders}와
     * 같은 패턴 — {@code CeremonyEffectDefinition}처럼 그룹으로 나뉘지 않은 단일 목록이라 그룹
     * 검증 없이 전체 id 집합 일치만 확인한다).
     */
    @Transactional
    public List<FaqDto.Response.FaqSummary> updateDisplayOrders(
            String actingPlatformRole, Long adminUserId, DisplayOrderRequest.UpdateDisplayOrders request
    ) {
        checkAllowed(actingPlatformRole);

        List<Faq> all = faqRepository.findAllByOrderByDisplayOrderAscIdAsc();
        Set<Long> allIds = all.stream().map(Faq::getId).collect(Collectors.toSet());
        Set<Long> requestedIds = request.getItems().stream().map(DisplayOrderRequest.Item::getId).collect(Collectors.toSet());
        if (!allIds.equals(requestedIds)) {
            throw new ApplicationException(SupportErrorCode.FAQ_ORDER_GROUP_MISMATCH);
        }

        Map<Long, Faq> byId = all.stream().collect(Collectors.toMap(Faq::getId, f -> f));
        for (DisplayOrderRequest.Item item : request.getItems()) {
            byId.get(item.getId()).updateDisplayOrder(item.getDisplayOrder());
        }

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.REORDER_FAQS, null, null,
                "faqIds=" + request.getItems().stream().map(DisplayOrderRequest.Item::getId).toList()
        );

        return all.stream()
                .sorted(Comparator.comparingInt(Faq::getDisplayOrder))
                .map(this::toSummary)
                .toList();
    }

    private FaqDto.Response.FaqSummary toSummary(Faq faq) {
        return new FaqDto.Response.FaqSummary(
                faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer(),
                faq.getDisplayOrder(), faq.isActive(), faq.getCreatedAt()
        );
    }

    private void checkAllowed(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, ACTION_FAQ_MANAGE)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
