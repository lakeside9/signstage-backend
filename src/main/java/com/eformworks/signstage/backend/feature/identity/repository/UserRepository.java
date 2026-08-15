package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.feature.identity.repository.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.entity.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    Page<User> findAllByStatus(UserStatus status, Pageable pageable);
}
