package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.feature.notification.entity.NotificationType;
import com.eformworks.signstage.backend.feature.notification.service.NotificationService;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.OrderInspection;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayout;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChange;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeStatus;
import com.eformworks.signstage.backend.feature.order.entity.ScopeChangeRequest;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 수주·발주·검수·지급 알림 문구와 받는 사람을 정한다 — signstage-docs
 * business/platform-order-partner-subcontract-review.md 12장(알림). 파트너 알림은 발주 응답 권한자
 * (지급은 지급 내역 권한자), 플랫폼 알림은 그 일을 처리할 액션 권한자에게 보낸다. 파트너 알림에는
 * 고객 계약금액을 넣지 않는다(D14).
 */
@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final String PARTNER_ORDER_PERMISSION = "ACTION_PARTNER_ORDER_RESPOND";
    private static final String PARTNER_PAYOUT_PERMISSION = "ACTION_PARTNER_PAYOUT_VIEW";
    private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NotificationService notificationService;

    public void partnerOrderIssued(PartnerOrder order) {
        notifyPartner(order, NotificationType.PARTNER_ORDER_ISSUED, "새 발주가 도착했습니다",
                title(order) + " · 발주 금액 " + money(order.getTotalAmount(), order.getCurrencyCode())
                        + " · 응답 기한 " + order.getResponseDueAt().format(DUE_FORMAT));
    }

    public void partnerOrderDueSoon(PartnerOrder order) {
        notifyPartner(order, NotificationType.PARTNER_ORDER_DUE_SOON, "발주 응답 기한이 다가옵니다",
                title(order) + " · 응답 기한 " + order.getResponseDueAt().format(DUE_FORMAT) + "까지 수락·거절해주세요.");
    }

    public void partnerOrderCancelled(PartnerOrder order, String reason) {
        notifyPartner(order, NotificationType.PARTNER_ORDER_CANCELLED, "발주가 취소되었습니다",
                title(order) + " · 사유: " + reason);
    }

    public void partnerOrderResponded(PartnerOrder order) {
        boolean accepted = order.getStatus() == PartnerOrderStatus.ACCEPTED;
        notificationService.notifyPlatform("ACTION_PARTNER_ORDER_ISSUE",
                accepted ? NotificationType.PARTNER_ORDER_ACCEPTED : NotificationType.PARTNER_ORDER_REJECTED,
                (accepted ? "발주 수락 · " : "발주 거절 · ") + order.getOrganization().getName(),
                accepted ? title(order) : title(order) + " · 사유: " + order.getRejectionReason(),
                salesOrderLink(order));
    }

    public void scopeChangeProposed(SalesOrderChange change) {
        if (change.getPartnerOrder() == null) {
            return;
        }
        PartnerOrder order = change.getPartnerOrder();
        notifyPartner(order, NotificationType.SCOPE_CHANGE_PROPOSED, "범위 변경 동의 요청이 왔습니다",
                title(order) + " · 발주 금액 " + money(change.getBeforePartnerAmount(), order.getCurrencyCode())
                        + " → " + money(change.getPartnerAmount(), order.getCurrencyCode()) + " · " + change.getReason());
    }

    public void scopeChangeResponded(SalesOrderChange change) {
        PartnerOrder order = change.getPartnerOrder();
        boolean rejected = change.getStatus() == SalesOrderChangeStatus.REJECTED;
        String message = rejected
                ? title(order) + " · 사유: " + change.getPartnerRejectionReason()
                : title(order) + (change.getStatus() == SalesOrderChangeStatus.APPLIED
                        ? " · 고객 수락도 받아 반영했습니다."
                        : " · 고객 수락을 기록하면 반영됩니다.");
        notificationService.notifyPlatform("ACTION_SALES_ORDER_MANAGE", NotificationType.SCOPE_CHANGE_RESPONDED,
                (rejected ? "범위 변경 거절 · " : "범위 변경 동의 · ") + order.getOrganization().getName(),
                message, "/admin/sales-orders/" + change.getSalesOrder().getId());
    }

    public void scopeChangeApplied(SalesOrderChange change) {
        if (change.getPartnerOrder() == null) {
            return;
        }
        PartnerOrder order = change.getPartnerOrder();
        notifyPartner(order, NotificationType.SCOPE_CHANGE_APPLIED, "범위 변경이 반영되었습니다",
                title(order) + " · 발주 금액 " + money(change.getPartnerAmount(), order.getCurrencyCode()));
    }

    public void scopeChangeRequested(ScopeChangeRequest request) {
        PartnerOrder order = request.getPartnerOrder();
        notificationService.notifyPlatform("ACTION_SALES_ORDER_MANAGE", NotificationType.SCOPE_CHANGE_REQUESTED,
                "범위 변경 요청 · " + order.getOrganization().getName(),
                title(order) + " · " + request.getContent(), salesOrderLink(order));
    }

    public void inspectionApproved(OrderInspection inspection) {
        PartnerOrder order = inspection.getPartnerOrder();
        String deduction = inspection.getDeductionAmount() != null && inspection.getDeductionAmount().signum() > 0
                ? " · 감액 " + money(inspection.getDeductionAmount(), order.getCurrencyCode())
                : "";
        notifyPartner(order, NotificationType.INSPECTION_APPROVED, "검수가 승인되었습니다",
                title(order) + " · 지급 대상 " + money(inspection.getPayableAmount(), order.getCurrencyCode()) + deduction);
    }

    public void inspectionDisputed(OrderInspection inspection) {
        PartnerOrder order = inspection.getPartnerOrder();
        notificationService.notifyPlatform("ACTION_INSPECTION_DISPUTE_RESOLVE", NotificationType.INSPECTION_DISPUTED,
                "감액 이의 제기 · " + order.getOrganization().getName(),
                title(order) + " · " + inspection.getDisputeReason(), "/admin/order-inspections");
    }

    public void inspectionDisputeResolved(OrderInspection inspection) {
        PartnerOrder order = inspection.getPartnerOrder();
        notifyPartner(order, NotificationType.INSPECTION_DISPUTE_RESOLVED, "감액 이의가 조정되었습니다",
                title(order) + " · 지급 대상 " + money(inspection.getPayableAmount(), order.getCurrencyCode())
                        + " · " + inspection.getDisputeResolutionNote());
    }

    public void payoutAwaitingApproval(PartnerPayout payout) {
        notificationService.notifyPlatform("ACTION_PARTNER_PAYOUT_LARGE_APPROVE",
                NotificationType.PARTNER_PAYOUT_APPROVAL_REQUIRED, "고액 지급 승인이 필요합니다",
                payout.getOrganization().getName() + " · " + payout.getSettlementMonth() + " 정산 · "
                        + money(payout.getAmount(), payout.getCurrencyCode()),
                "/admin/partner-payouts");
    }

    public void payoutPaid(PartnerPayout payout) {
        notificationService.notifyOrganization(payout.getOrganization().getId(), PARTNER_PAYOUT_PERMISSION,
                NotificationType.PARTNER_PAYOUT_PAID, "지급이 완료되었습니다",
                payout.getSettlementMonth() + " 정산 · " + money(payout.getAmount(), payout.getCurrencyCode()) + "(공급가)",
                "/partner-payouts/" + payout.getOrganization().getId());
    }

    public void invoiceOverdue(CustomerInvoice invoice) {
        notificationService.notifyPlatform("ACTION_CUSTOMER_INVOICE_MANAGE", NotificationType.CUSTOMER_INVOICE_OVERDUE,
                "입금 기한이 지난 청구가 있습니다",
                invoice.getSalesOrder().getCustomer().getName() + " · " + invoice.getSalesOrder().getTitle() + " · "
                        + money(invoice.getTotalAmount(), invoice.getCurrencyCode()) + " · 기한 " + invoice.getDueDate(),
                "/admin/sales-orders/" + invoice.getSalesOrder().getId());
    }

    private void notifyPartner(PartnerOrder order, NotificationType type, String title, String message) {
        Long organizationId = order.getOrganization().getId();
        notificationService.notifyOrganization(organizationId, PARTNER_ORDER_PERMISSION, type, title, message,
                "/partner-orders/" + organizationId + "/" + order.getId());
    }

    private static String salesOrderLink(PartnerOrder order) {
        return "/admin/sales-orders/" + order.getSalesOrder().getId();
    }

    private static String title(PartnerOrder order) {
        return order.getSalesOrder().getTitle();
    }

    private static String money(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return "-";
        }
        String number = NumberFormat.getNumberInstance(Locale.KOREA).format(amount.stripTrailingZeros());
        return "KRW".equals(currencyCode) ? number + "원" : number + " " + currencyCode;
    }
}
