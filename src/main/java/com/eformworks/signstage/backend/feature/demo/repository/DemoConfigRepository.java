package com.eformworks.signstage.backend.feature.demo.repository;

import com.eformworks.signstage.backend.feature.demo.entity.DemoConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoConfigRepository extends JpaRepository<DemoConfig, Long> {

    Optional<DemoConfig> findBySlug(String slug);

    List<DemoConfig> findAllByOrderBySlugAsc();
}
