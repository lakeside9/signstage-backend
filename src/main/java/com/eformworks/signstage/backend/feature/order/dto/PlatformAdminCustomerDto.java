package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.Customer;
import com.eformworks.signstage.backend.feature.order.entity.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PlatformAdminCustomerDto {

    private PlatformAdminCustomerDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SaveCustomer {

            @NotBlank
            @Size(max = 200)
            private String name;

            @Size(max = 20)
            private String businessRegistrationNumber;

            @Size(max = 100)
            private String representativeName;

            @Size(max = 100)
            private String contactName;

            @Size(max = 20)
            private String contactPhone;

            @Email
            @Size(max = 255)
            private String contactEmail;

            @Email
            @Size(max = 255)
            private String taxInvoiceEmail;

            @Size(max = 500)
            private String address;

            @Size(max = 1000)
            private String memo;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChangeStatus {

            @NotNull
            private CustomerStatus status;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class AdminCustomer {

            private final Long id;
            private final String name;
            private final String businessRegistrationNumber;
            private final String representativeName;
            private final String contactName;
            private final String contactPhone;
            private final String contactEmail;
            private final String taxInvoiceEmail;
            private final String address;
            private final String memo;
            private final String status;
            private final LocalDateTime createdAt;

            public static AdminCustomer from(Customer customer) {
                return new AdminCustomer(
                        customer.getId(),
                        customer.getName(),
                        customer.getBusinessRegistrationNumber(),
                        customer.getRepresentativeName(),
                        customer.getContactName(),
                        customer.getContactPhone(),
                        customer.getContactEmail(),
                        customer.getTaxInvoiceEmail(),
                        customer.getAddress(),
                        customer.getMemo(),
                        customer.getStatus().name(),
                        customer.getCreatedAt()
                );
            }
        }
    }
}
