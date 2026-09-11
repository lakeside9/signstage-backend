package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.BillingQuoteDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuote;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuoteLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuoteStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuoteStatusEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingQuoteLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingQuoteRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingQuoteStatusEventRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 견적(billing quote) — signstage-docs
 * business/currency-tax-internationalization-review.md 9/10장 결정(2026-09-10 구현). "예상
 * 청구 금액"({@link CeremonyService#calculateEstimatedTotal})과 정확히 같은 계산
 * ({@link CeremonyService#buildQuoteCalculation})을 특정 시점에 스냅샷으로 고정한다 — 이후
 * 카탈로그·세금 정책·할인이 바뀌어도 이미 확정된 견적은 절대 바뀌지 않는다(append-only).
 * 재견적은 새 버전을 추가하는 것으로 표현하고(기존 버전을 지우거나 자동 무효화하지 않음),
 * 무효화는 {@link BillingQuoteStatusEvent}에 VOID 이벤트를 추가하는 것으로 표현한다.
 *
 * <p>권한은 {@link CeremonyService}의 다른 행사 셀프서비스 기능(플랜 변경, 추가구매 등)과 같은
 * 등급을 쓴다 — 확정/무효화는 {@code checkCeremonyManageAccess}(조직 관리 가능 멤버), 조회는
 * {@code checkCeremonyReadAccess}(조직 멤버 누구나, OPERATOR는 배정된 행사만).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingQuoteService {

    private final BillingQuoteRepository billingQuoteRepository;
    private final BillingQuoteLineRepository billingQuoteLineRepository;
    private final BillingQuoteStatusEventRepository billingQuoteStatusEventRepository;
    private final CeremonyService ceremonyService;
    private final UserRepository userRepository;

    @Transactional
    public BillingQuoteDto.Response.QuoteDetail finalizeQuote(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        CeremonyService.QuoteCalculation calculation = ceremonyService.buildQuoteCalculation(ceremony);
        if (calculation.lines().isEmpty()) {
            throw new ApplicationException(CeremonyErrorCode.QUOTE_EMPTY);
        }

        int nextVersion = billingQuoteRepository.findMaxVersion(ceremonyId) + 1;
        LocalDate taxPointDate = LocalDate.now(ZoneId.of(ceremony.getTimeZoneId()));

        BillingQuote quote = BillingQuote.builder()
                .ceremony(ceremony)
                .version(nextVersion)
                .currencyCode(ceremony.getCurrencyCode())
                .currencyFractionDigits(ceremony.getCurrencyFractionDigits())
                .currencyRoundingMode(ceremony.getCurrencyRoundingMode())
                .netAmount(calculation.netAmount())
                .discountAmount(calculation.subtotal().subtract(calculation.netAmount()))
                .taxAmount(calculation.taxAmount())
                .grossAmount(calculation.grossAmount())
                .pricingCalculatedAt(LocalDateTime.now())
                .taxPointDate(taxPointDate)
                .build();
        billingQuoteRepository.save(quote);

        for (CeremonyService.QuoteLineDetail line : calculation.lines()) {
            billingQuoteLineRepository.save(BillingQuoteLine.builder()
                    .billingQuote(quote)
                    .lineType(line.lineType())
                    .itemId(line.itemId())
                    .itemName(line.itemName())
                    .category(line.category())
                    .quantity(line.quantity())
                    .unitListAmount(line.unitListAmount())
                    .listAmount(line.listAmount())
                    .itemDiscountAmount(line.itemDiscountAmount())
                    .ceremonyDiscountAmount(line.ceremonyDiscountAmount())
                    .netAmount(line.netAmount())
                    .taxCode(line.taxCode())
                    .taxCategory(line.taxCategory())
                    .taxRatePercent(line.taxRatePercent())
                    .priceInclusion(line.priceInclusion())
                    .taxAmount(line.taxAmount())
                    .grossAmount(line.grossAmount())
                    .build());
        }

        billingQuoteStatusEventRepository.save(BillingQuoteStatusEvent.builder()
                .billingQuote(quote)
                .status(BillingQuoteStatus.FINALIZED)
                .actorId(currentUserId)
                .build());

        return toDetail(quote);
    }

    @Transactional
    public BillingQuoteDto.Response.QuoteSummary voidQuote(
            Long organizationId,
            Long ceremonyId,
            Long quoteId,
            Long currentUserId,
            BillingQuoteDto.Request.VoidQuote request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        BillingQuote quote = billingQuoteRepository.findByIdAndCeremonyId(quoteId, ceremonyId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.QUOTE_NOT_FOUND));
        if (resolveLatestEvent(quote.getId())
                .map(event -> event.getStatus() == BillingQuoteStatus.VOID)
                .orElse(false)) {
            throw new ApplicationException(CeremonyErrorCode.QUOTE_ALREADY_VOID);
        }

        billingQuoteStatusEventRepository.save(BillingQuoteStatusEvent.builder()
                .billingQuote(quote)
                .status(BillingQuoteStatus.VOID)
                .reason(request.getReason())
                .actorId(currentUserId)
                .build());

        return toSummary(quote);
    }

    public List<BillingQuoteDto.Response.QuoteSummary> findQuotes(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return billingQuoteRepository.findAllByCeremonyIdOrderByVersionDesc(ceremonyId).stream()
                .map(this::toSummary)
                .toList();
    }

    public BillingQuoteDto.Response.QuoteDetail findQuoteDetail(
            Long organizationId, Long ceremonyId, Long quoteId, Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        BillingQuote quote = billingQuoteRepository.findByIdAndCeremonyId(quoteId, ceremonyId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.QUOTE_NOT_FOUND));
        return toDetail(quote);
    }

    private Optional<BillingQuoteStatusEvent> resolveLatestEvent(Long quoteId) {
        return billingQuoteStatusEventRepository.findAllByBillingQuoteIdOrderByOccurredAtDescIdDesc(quoteId).stream()
                .findFirst();
    }

    private BillingQuoteDto.Response.QuoteSummary toSummary(BillingQuote quote) {
        BillingQuoteStatusEvent latest = resolveLatestEvent(quote.getId()).orElse(null);
        String createdByLoginId = userRepository.findById(quote.getCreatedBy()).map(User::getLoginId).orElse(null);
        return new BillingQuoteDto.Response.QuoteSummary(
                quote.getId(),
                quote.getVersion(),
                latest != null ? latest.getStatus().name() : BillingQuoteStatus.FINALIZED.name(),
                quote.getCurrencyCode(),
                quote.getCurrencyFractionDigits(),
                quote.getNetAmount(),
                quote.getDiscountAmount(),
                quote.getTaxAmount(),
                quote.getGrossAmount(),
                quote.getPricingCalculatedAt(),
                quote.getTaxPointDate(),
                createdByLoginId,
                quote.getCreatedAt(),
                latest != null && latest.getStatus() == BillingQuoteStatus.VOID ? latest.getReason() : null
        );
    }

    private BillingQuoteDto.Response.QuoteDetail toDetail(BillingQuote quote) {
        List<BillingQuoteDto.Response.QuoteLineSummary> lines = billingQuoteLineRepository
                .findAllByBillingQuoteIdOrderByIdAsc(quote.getId()).stream()
                .map(line -> new BillingQuoteDto.Response.QuoteLineSummary(
                        line.getLineType(),
                        line.getItemId(),
                        line.getItemName(),
                        line.getCategory().name(),
                        line.getQuantity(),
                        line.getUnitListAmount(),
                        line.getListAmount(),
                        line.getItemDiscountAmount(),
                        line.getCeremonyDiscountAmount(),
                        line.getNetAmount(),
                        line.getTaxCode(),
                        line.getTaxCategory(),
                        line.getTaxRatePercent(),
                        line.getPriceInclusion(),
                        line.getTaxAmount(),
                        line.getGrossAmount()
                ))
                .toList();
        return new BillingQuoteDto.Response.QuoteDetail(toSummary(quote), lines);
    }
}
