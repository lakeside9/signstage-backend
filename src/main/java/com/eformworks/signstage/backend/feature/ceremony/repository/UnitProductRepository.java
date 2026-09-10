package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitProductRepository extends JpaRepository<UnitProduct, Long> {

    List<UnitProduct> findAllByIdIn(List<Long> ids);
}
