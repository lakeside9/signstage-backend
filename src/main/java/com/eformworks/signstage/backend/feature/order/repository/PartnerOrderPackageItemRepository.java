package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderPackageItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerOrderPackageItemRepository extends JpaRepository<PartnerOrderPackageItem, Long> {

    List<PartnerOrderPackageItem> findAllByPartnerOrderIdOrderByDisplayOrderAsc(Long partnerOrderId);
}
