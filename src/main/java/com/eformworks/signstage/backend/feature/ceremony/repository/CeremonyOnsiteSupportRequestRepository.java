package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOnsiteSupportRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyOnsiteSupportRequestRepository
        extends JpaRepository<CeremonyOnsiteSupportRequest, Long>, CeremonyOnsiteSupportRequestRepositoryCustom {

    /** 파트너 쪽 이 행사의 요청 목록 — 최신순. */
    List<CeremonyOnsiteSupportRequest> findAllByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);

    /** DRAFT 행사 삭제({@code CeremonyService#deleteCeremony}) 시 이 행사의 요청을 함께 지운다. */
    void deleteAllByCeremonyId(Long ceremonyId);
}
