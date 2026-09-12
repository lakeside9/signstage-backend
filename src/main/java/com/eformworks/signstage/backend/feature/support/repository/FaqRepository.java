package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.Faq;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    /** {@code /api/faqs} — 인증된 사용자 누구나, 활성 FAQ만 표시 순서대로. */
    List<Faq> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    /** 표시 순서 일괄 변경 시 전체 목록이 요청과 정확히 일치하는지 검증하려고 통째로 읽는다. */
    List<Faq> findAllByOrderByDisplayOrderAscIdAsc();

    Page<Faq> findAllByActive(boolean active, Pageable pageable);
}
