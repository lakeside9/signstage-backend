package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.feature.identity.repository.entity.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
}
