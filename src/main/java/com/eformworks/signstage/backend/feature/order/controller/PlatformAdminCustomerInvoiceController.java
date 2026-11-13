package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.CustomerInvoiceDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.service.CustomerInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 고객 청구·수납 API(signstage-docs business/platform-order-partner-subcontract-review.md D12·D16). */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 고객 청구·수납 API")
@RestController
@RequestMapping("/api/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminCustomerInvoiceController {

    private final CustomerInvoiceService customerInvoiceService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "청구 목록 조회", description = "overdueOnly=true면 입금 기한이 지난 미입금 청구만 본다.")
    @GetMapping("/customer-invoices")
    public ApiResponse<PageResponse<CustomerInvoiceDto.Response.InvoiceSummary>> findInvoices(
            @RequestParam(required = false) CustomerInvoiceStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(customerInvoiceService.findInvoices(status, customerId, overdueOnly, pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "수주 청구 현황 조회")
    @GetMapping("/sales-orders/{salesOrderId}/invoices")
    public ApiResponse<CustomerInvoiceDto.Response.SalesOrderBilling> retrieveSalesOrderBilling(@PathVariable Long salesOrderId) {
        return ApiResponse.success(customerInvoiceService.retrieveSalesOrderBilling(salesOrderId), traceIdProvider.getTraceId());
    }

    @Operation(summary = "선금·잔금 청구", description = "선금은 수주 확정 후, 잔금은 행사 완료 후 발행한다. ACTION_CUSTOMER_INVOICE_MANAGE 권한이 필요하다.")
    @PostMapping("/sales-orders/{salesOrderId}/invoices")
    public ApiResponse<CustomerInvoiceDto.Response.SalesOrderBilling> issueInvoice(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @Valid @RequestBody CustomerInvoiceDto.Request.Issue request
    ) {
        return ApiResponse.success(
                customerInvoiceService.issueInvoice(salesOrderId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "입금 기록")
    @PostMapping("/customer-invoices/{invoiceId}/payment")
    public ApiResponse<CustomerInvoiceDto.Response.InvoiceSummary> recordPayment(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long invoiceId,
            @Valid @RequestBody CustomerInvoiceDto.Request.RecordPayment request
    ) {
        return ApiResponse.success(
                customerInvoiceService.recordPayment(invoiceId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "세금계산서 발행 기록", description = "시스템 밖에서 발행한 세금계산서의 발행일·승인번호를 기록한다(D16).")
    @PostMapping("/customer-invoices/{invoiceId}/tax-invoice")
    public ApiResponse<CustomerInvoiceDto.Response.InvoiceSummary> recordTaxInvoice(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long invoiceId,
            @Valid @RequestBody CustomerInvoiceDto.Request.RecordTaxInvoice request
    ) {
        return ApiResponse.success(
                customerInvoiceService.recordTaxInvoice(invoiceId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "청구 취소", description = "입금 전 청구만 취소한다.")
    @PostMapping("/customer-invoices/{invoiceId}/cancel")
    public ApiResponse<CustomerInvoiceDto.Response.InvoiceSummary> cancelInvoice(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long invoiceId,
            @Valid @RequestBody CustomerInvoiceDto.Request.Cancel request
    ) {
        return ApiResponse.success(
                customerInvoiceService.cancelInvoice(invoiceId, currentUser.userId(), currentUser.platformRole(), request.getReason()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "환불 완료 기록", description = "고객 취소로 생긴 환불을 시스템 밖에서 돌려준 뒤 환불일을 기록한다.")
    @PostMapping("/customer-refunds/{refundId}/refunded")
    public ApiResponse<CustomerInvoiceDto.Response.SalesOrderBilling> recordRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long refundId,
            @Valid @RequestBody CustomerInvoiceDto.Request.RecordRefund request
    ) {
        return ApiResponse.success(
                customerInvoiceService.recordRefund(refundId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }
}
