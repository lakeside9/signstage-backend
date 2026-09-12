package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyInquiry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyInquiryRepository extends JpaRepository<CeremonyInquiry, Long>, CeremonyInquiryRepositoryCustom {

    /** 파트너 쪽 이 행사의 문의 목록 — 최근 갱신순(답변 대기가 위로 오도록). */
    List<CeremonyInquiry> findAllByCeremonyIdOrderByLastMessageAtDesc(Long ceremonyId);

    /** DRAFT 행사 삭제({@code CeremonyService#deleteCeremony}) 시 이 행사의 문의를 함께 지운다. */
    void deleteAllByCeremonyId(Long ceremonyId);
}
