package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.entity.TaxPolicy;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.TaxPolicyRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaxPolicyResolver {

    private final TaxPolicyRepository taxPolicyRepository;

    public TaxPolicy resolve(String countryCode, String taxCode, LocalDate taxPointDate) {
        return taxPolicyRepository.findEffectivePolicy(countryCode, taxCode, taxPointDate)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TAX_POLICY_NOT_FOUND));
    }
}
