package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminCustomerDto;
import com.eformworks.signstage.backend.feature.order.entity.Customer;
import com.eformworks.signstage.backend.feature.order.entity.CustomerStatus;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.CustomerRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 수주 고객 관리 — signstage-docs business/platform-order-partner-subcontract-review.md D02.
 * 조회는 플랫폼 관리자 누구나, 등록·수정은 {@code ACTION_CUSTOMER_MANAGE}가 필요하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public Page<PlatformAdminCustomerDto.Response.AdminCustomer> findCustomers(
            String keyword,
            CustomerStatus status,
            Pageable pageable
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return customerRepository.search(normalizedKeyword, status, pageable)
                .map(PlatformAdminCustomerDto.Response.AdminCustomer::from);
    }

    public PlatformAdminCustomerDto.Response.AdminCustomer retrieveCustomer(Long customerId) {
        return PlatformAdminCustomerDto.Response.AdminCustomer.from(findCustomerOrThrow(customerId));
    }

    @Transactional
    public PlatformAdminCustomerDto.Response.AdminCustomer createCustomer(
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminCustomerDto.Request.SaveCustomer request
    ) {
        checkCanManage(actingPlatformRole);
        Customer customer = customerRepository.save(Customer.builder()
                .name(request.getName())
                .businessRegistrationNumber(request.getBusinessRegistrationNumber())
                .representativeName(request.getRepresentativeName())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .taxInvoiceEmail(request.getTaxInvoiceEmail())
                .address(request.getAddress())
                .memo(request.getMemo())
                .build());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CREATE_CUSTOMER, null, null,
                "customerId=" + customer.getId() + ", name=" + customer.getName());
        return PlatformAdminCustomerDto.Response.AdminCustomer.from(customer);
    }

    @Transactional
    public PlatformAdminCustomerDto.Response.AdminCustomer updateCustomer(
            Long customerId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminCustomerDto.Request.SaveCustomer request
    ) {
        checkCanManage(actingPlatformRole);
        Customer customer = findCustomerOrThrow(customerId);
        customer.updateInfo(
                request.getName(), request.getBusinessRegistrationNumber(), request.getRepresentativeName(),
                request.getContactName(), request.getContactPhone(), request.getContactEmail(),
                request.getTaxInvoiceEmail(), request.getAddress(), request.getMemo()
        );
        auditLogRecorder.record(adminUserId, PlatformAdminAction.UPDATE_CUSTOMER, null, null,
                "customerId=" + customerId + ", name=" + customer.getName());
        return PlatformAdminCustomerDto.Response.AdminCustomer.from(customer);
    }

    @Transactional
    public PlatformAdminCustomerDto.Response.AdminCustomer changeCustomerStatus(
            Long customerId,
            Long adminUserId,
            String actingPlatformRole,
            CustomerStatus status
    ) {
        checkCanManage(actingPlatformRole);
        Customer customer = findCustomerOrThrow(customerId);
        customer.changeStatus(status);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.UPDATE_CUSTOMER, null, null,
                "customerId=" + customerId + ", status=" + status);
        return PlatformAdminCustomerDto.Response.AdminCustomer.from(customer);
    }

    Customer findCustomerOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.CUSTOMER_NOT_FOUND));
    }

    private void checkCanManage(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_CUSTOMER_MANAGE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
