package com.eformworks.signstage.backend.feature.permission.repository;

import com.eformworks.signstage.backend.feature.permission.entity.Menu;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findAllByConsoleOrderByDisplayOrderAsc(RoleAxis console);
}
