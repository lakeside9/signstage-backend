package com.eformworks.signstage.backend.feature.order.model;

/**
 * 플랜·옵션 밖의 수행 범위 — signstage-docs business/platform-order-partner-subcontract-review.md D23·D24·D26.
 *
 * <ul>
 *   <li>{@code spareTabletCount}: 예비 태블릿. 발주에만 넣어 대당 단가로 지급하고 고객에게는 청구하지 않는다(D23).</li>
 *   <li>{@code rehearsalStaffPersonDays}: 본행사와 다른 날 리허설에 현장 방문하는 인·일. 고객 견적과 발주에
 *       현장 인력 인·일 가격으로 더한다. 같은 날 리허설은 본행사 인·일에 포함하므로 넣지 않는다(D24).</li>
 *   <li>{@code lodgingNights} × {@code lodgingPersons}: 관리자가 숙박이 필요하다고 정한 박수와 인원. 권역 할증표의
 *       1박 금액(고객·파트너)을 곱해 더한다(D26). 둘 다 0이거나 둘 다 1 이상이다.</li>
 * </ul>
 */
public record ScopeExtras(int spareTabletCount, int rehearsalStaffPersonDays, int lodgingNights, int lodgingPersons) {

    public static final ScopeExtras NONE = new ScopeExtras(0, 0, 0, 0);

    /** 요청에서 생략한 값은 0이다. */
    public static ScopeExtras of(Integer spareTabletCount, Integer rehearsalStaffPersonDays, Integer lodgingNights, Integer lodgingPersons) {
        return new ScopeExtras(orZero(spareTabletCount), orZero(rehearsalStaffPersonDays), orZero(lodgingNights), orZero(lodgingPersons));
    }

    public int lodgingPersonNights() {
        return lodgingNights * lodgingPersons;
    }

    /** 음수가 없고, 숙박은 박수·인원이 함께 있거나 함께 없어야 한다. */
    public boolean isValid() {
        return spareTabletCount >= 0 && rehearsalStaffPersonDays >= 0 && lodgingNights >= 0 && lodgingPersons >= 0
                && (lodgingNights == 0) == (lodgingPersons == 0);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
