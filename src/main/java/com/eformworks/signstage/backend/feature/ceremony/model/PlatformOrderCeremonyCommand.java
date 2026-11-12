package com.eformworks.signstage.backend.feature.ceremony.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발주 수락 시 플랫폼 수주 행사를 만드는 입력 — 수주(고객 계약)에서 확정된 제공 범위와 일정을
 * 그대로 옮긴다. 고객 계약금액·고객 사업자 정보는 담지 않는다(signstage-docs
 * business/platform-order-partner-subcontract-review.md D14 — 파트너는 운영에 필요한 정보만 본다).
 *
 * @param options 플랜 포함 수량 외에 수주에서 추가한 단위 상품(태블릿·인력·효과 묶음 등)
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
        List<ScopeOption> options
) {

    public record ScopeOption(Long unitProductId, int quantity) {
    }
}
