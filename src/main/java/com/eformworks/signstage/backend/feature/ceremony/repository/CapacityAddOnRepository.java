package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapacityAddOnRepository extends JpaRepository<CapacityAddOn, Long> {

    List<CapacityAddOn> findAllByIdIn(List<Long> ids);
}
