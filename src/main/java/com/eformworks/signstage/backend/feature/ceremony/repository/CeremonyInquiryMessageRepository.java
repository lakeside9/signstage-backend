package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyInquiryMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyInquiryMessageRepository extends JpaRepository<CeremonyInquiryMessage, Long> {

    List<CeremonyInquiryMessage> findAllByInquiryIdOrderByCreatedAtAsc(Long inquiryId);

    void deleteAllByInquiry_CeremonyId(Long ceremonyId);
}
