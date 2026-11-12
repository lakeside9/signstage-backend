package com.eformworks.signstage.backend.feature.ceremony.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발주 수락 시 플랫폼 수주 행사를 만드는 입력 — 수주(고객 계약)에서 확정된 제공 범위와 일정을
 * 그대로 옮긴다. 고객 계약금액·고객 사업자 정보는 담지 않는다(signstage-docs
 * business/platform-order-partner-subcontract-review.md D14 — 파트너는 운영에 필요한 정보만 본다).
 *
 * @param options 플랜 포함 수량 외에 수주에서 추가한 단위 상품(태블릿·인력·효과 묶음 등)
 * @param currencyCode 수주 통화 — 수행 조직의 과금 통화와 같아야 한다
 * @param planTaxCode 수주 패키지 줄의 세금 코드 — 플랜 포함 단위 상품 스냅샷에 쓴다
 */
public record PlatformOrderCeremonyCommand(
        Long organizationId,
        Long acceptingUserId,
        Long billingPlanId,
        String title,
        String location,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String organizingInstitution,
        String contactName,
        String contactPhone,
        String contactEmail,
        List<ScopeOption> options,
        String currencyCode,
        String planTaxCode
) {

    /**
     * @param taxCode 수주(또는 범위 변경) 줄에 스냅샷한 세금 코드 — 플랫폼 수주 단위 상품은 판매 가격
     *                기간이 없어 여기서 가져온다(signstage-docs
     *                business/platform-order-unit-product-composition-review.md U05)
     */
    public record ScopeOption(Long unitProductId, int quantity, String taxCode) {
    }
}
