package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyRepository extends JpaRepository<Ceremony, Long>, CeremonyRepositoryCustom {

    List<Ceremony> findAllByOrganizationId(Long organizationId);
}
