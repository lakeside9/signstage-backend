package com.eformworks.signstage.backend.core.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyPlanHistoryUnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscountHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductHistory;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OrganizationBillingPlanDiscountHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductHistoryRepository;
import com.eformworks.signstage.backend.feature.identity.entity.LoginHistory;
import com.eformworks.signstage.backend.feature.identity.entity.UserHistory;
import com.eformworks.signstage.backend.feature.identity.repository.LoginHistoryRepository;
import com.eformworks.signstage.backend.feature.identity.repository.UserHistoryRepository;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationHistory;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationHistoryRepository;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAuditLog;
import com.eformworks.signstage.backend.feature.platformadmin.repository.PlatformAdminAuditLogRepository;
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;

class AppendOnlyHistoryContractTest {

    private static final List<Class<?>> APPEND_ONLY_ENTITIES = List.of(
            BillingPlanHistory.class,
            BillingPlanHistoryUnitProduct.class,
            CeremonyEventLog.class,
            CeremonyPlanHistory.class,
            CeremonyPlanHistoryUnitProduct.class,
            UnitProductHistory.class,
            OrganizationBillingPlanDiscountHistory.class,
            LoginHistory.class,
            UserHistory.class,
            OrganizationHistory.class,
            PlatformAdminAuditLog.class
    );

    private static final List<Class<?>> APPEND_ONLY_REPOSITORIES = List.of(
            BillingPlanHistoryRepository.class,
            BillingPlanHistoryUnitProductRepository.class,
            CeremonyEventLogRepository.class,
            CeremonyPlanHistoryUnitProductRepository.class,
            CeremonyPlanHistoryRepository.class,
            UnitProductHistoryRepository.class,
            OrganizationBillingPlanDiscountHistoryRepository.class,
            LoginHistoryRepository.class,
            UserHistoryRepository.class,
            OrganizationHistoryRepository.class,
            PlatformAdminAuditLogRepository.class
    );

    @Test
    void allHistoryEntitiesAreImmutable() {
        assertThat(APPEND_ONLY_ENTITIES)
                .allMatch(entity -> entity.isAnnotationPresent(Immutable.class));
    }

    @Test
    void historyRepositoriesDoNotExposeCrudDeleteMethods() {
        assertThat(APPEND_ONLY_REPOSITORIES)
                .allMatch(AppendOnlyRepository.class::isAssignableFrom)
                .noneMatch(CrudRepository.class::isAssignableFrom);
    }
}
