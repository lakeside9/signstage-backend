package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.Faq;
import com.eformworks.signstage.backend.feature.support.entity.SupportAudience;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long>, FaqRepositoryCustom {

    /** 대상별 활성 FAQ — 파트너용은 {@code /api/faqs}, 고객용은 공개 {@code /api/public/faqs}. 표시 순서대로. */
    List<Faq> findAllByAudienceAndActiveTrueOrderByDisplayOrderAscIdAsc(SupportAudience audience);

    /** 표시 순서는 대상별로 매긴다 — 일괄 변경 시 그 대상의 전체 목록이 요청과 정확히 일치하는지 검증하려고 통째로 읽는다. */
    List<Faq> findAllByAudienceOrderByDisplayOrderAscIdAsc(SupportAudience audience);
}
