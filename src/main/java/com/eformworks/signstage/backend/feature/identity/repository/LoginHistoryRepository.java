package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.identity.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LoginHistoryRepository extends AppendOnlyRepository<LoginHistory, Long> {

    Page<LoginHistory> findAllByUserId(Long userId, Pageable pageable);
}
