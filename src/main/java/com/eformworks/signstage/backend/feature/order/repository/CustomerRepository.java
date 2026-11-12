package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.Customer;
import com.eformworks.signstage.backend.feature.order.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("""
            select c from Customer c
            where (:keyword is null or c.name like concat('%', :keyword, '%')
                   or c.businessRegistrationNumber like concat('%', :keyword, '%'))
              and (:status is null or c.status = :status)
            """)
    Page<Customer> search(@Param("keyword") String keyword, @Param("status") CustomerStatus status, Pageable pageable);
}
