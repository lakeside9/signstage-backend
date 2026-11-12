package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminCustomerDto;
import com.eformworks.signstage.backend.feature.order.entity.CustomerStatus;
import com.eformworks.signstage.backend.feature.order.service.CustomerService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 플랫폼 수주 고객 관리 API(D02). 등록·수정은 ACTION_CUSTOMER_MANAGE 권한이 필요하다. */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 고객 관리 API")
@RestController
@RequestMapping("/api/platform-admin/customers")
@RequiredArgsConstructor
public class PlatformAdminCustomerController {

    private final CustomerService customerService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "고객 목록 조회", description = "keyword는 상호·사업자번호 부분 일치다.")
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminCustomerDto.Response.AdminCustomer>> findCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(customerService.findCustomers(keyword, status, pageable)), traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "고객 상세 조회")
    @GetMapping("/{customerId}")
    public ApiResponse<PlatformAdminCustomerDto.Response.AdminCustomer> retrieveCustomer(@PathVariable Long customerId) {
        return ApiResponse.success(customerService.retrieveCustomer(customerId), traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객 등록")
    @PostMapping
    public ApiResponse<PlatformAdminCustomerDto.Response.AdminCustomer> createCustomer(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PlatformAdminCustomerDto.Request.SaveCustomer request
    ) {
        PlatformAdminCustomerDto.Response.AdminCustomer response =
                customerService.createCustomer(currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객 정보 수정")
    @PutMapping("/{customerId}")
    public ApiResponse<PlatformAdminCustomerDto.Response.AdminCustomer> updateCustomer(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long customerId,
            @Valid @RequestBody PlatformAdminCustomerDto.Request.SaveCustomer request
    ) {
        PlatformAdminCustomerDto.Response.AdminCustomer response =
                customerService.updateCustomer(customerId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객 사용 여부 변경", description = "INACTIVE 고객으로는 새 견적을 만들 수 없다.")
    @PutMapping("/{customerId}/status")
    public ApiResponse<PlatformAdminCustomerDto.Response.AdminCustomer> changeCustomerStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long customerId,
            @Valid @RequestBody PlatformAdminCustomerDto.Request.ChangeStatus request
    ) {
        PlatformAdminCustomerDto.Response.AdminCustomer response = customerService.changeCustomerStatus(
                customerId, currentUser.userId(), currentUser.platformRole(), request.getStatus()
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
