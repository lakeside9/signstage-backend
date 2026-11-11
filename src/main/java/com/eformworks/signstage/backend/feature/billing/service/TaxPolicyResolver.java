package com.eformworks.signstage.backend.feature.billing.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.error.BillingErrorCode;
import com.eformworks.signstage.backend.feature.billing.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.billing.repository.TaxPolicyRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaxPolicyResolver {

    private final TaxPolicyRepository taxPolicyRepository;

    public TaxPolicy resolve(String countryCode, String taxCode, LocalDate taxPointDate) {
        return taxPolicyRepository.findEffectivePolicy(countryCode, taxCode, taxPointDate)
                .orElseThrow(() -> new ApplicationException(BillingErrorCode.TAX_POLICY_NOT_FOUND));
    }
}
