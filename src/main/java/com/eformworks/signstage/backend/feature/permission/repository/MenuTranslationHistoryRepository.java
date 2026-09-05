package com.eformworks.signstage.backend.feature.permission.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.permission.entity.MenuTranslationHistory;

public interface MenuTranslationHistoryRepository extends AppendOnlyRepository<MenuTranslationHistory, Long> {
}
