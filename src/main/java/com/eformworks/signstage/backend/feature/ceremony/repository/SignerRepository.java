package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignerRepository extends JpaRepository<Signer, Long> {

    List<Signer> findAllByCeremonyId(Long ceremonyId);

    long countByCeremonyId(Long ceremonyId);

    boolean existsByAccessKey(String accessKey);

    Optional<Signer> findByAccessKey(String accessKey);
}
