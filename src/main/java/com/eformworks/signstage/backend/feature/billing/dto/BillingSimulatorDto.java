package com.eformworks.signstage.backend.feature.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 파트너사 과금 시뮬레이터(`/api/organizations/{organizationId}/billing-simulator`) 응답 DTO.
 * 공급가와 타 조직 정보는 담지 않는다.
 */
public final class BillingSimulatorDto {

    private BillingSimulatorDto() {
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class Product {

            private final Long id;
            private final String name;
            private final String description;
            private final String type;
            private final int includedQuantity;
            private final BigDecimal salePrice;
            private final int saleUnitQuantity;
            private final Integer maxPurchaseQuantity;
        }

        @Getter
        @AllArgsConstructor
        public static class Plan {

            private final Long id;
            private final String name;
            private final boolean subscription;
            private final BigDecimal subtotal;
            private final BigDecimal appliedPrice;
            private final boolean partnerDiscount;
            private final List<Product> products;
        }

        @Getter
        @AllArgsConstructor
        public static class Catalog {

            private final String organizationName;
            private final String currencyCode;
            private final LocalDate asOfDate;
            private final List<Plan> plans;
            private final List<Product> additionalProducts;
        }
    }
}
