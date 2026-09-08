package com.eformworks.signstage.backend.feature.permission.repository;

import com.eformworks.signstage.backend.feature.permission.entity.Menu;
import com.eformworks.signstage.backend.feature.permission.entity.MenuTranslation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuTranslationRepository extends JpaRepository<MenuTranslation, Long> {

    List<MenuTranslation> findAllByMenuIdIn(List<Long> menuIds);

    Optional<MenuTranslation> findByMenuAndLanguageCode(Menu menu, String languageCode);
}
