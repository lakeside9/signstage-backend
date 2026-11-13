package com.eformworks.signstage.backend.feature.support.repository;

import com.eformworks.signstage.backend.feature.support.entity.ContactInquiry;
import com.eformworks.signstage.backend.feature.support.entity.ContactInquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** {@link FaqRepositoryCustom}과 같은 패턴 — 관리자 목록의 동적 검색 조건. */
public interface ContactInquiryRepositoryCustom {

    /** status·keyword 모두 선택 필터다. keyword는 회사명·담당자·이메일·내용 중 하나라도 포함하면 매칭된다. 최근 접수 순. */
    Page<ContactInquiry> search(ContactInquiryStatus status, String keyword, Pageable pageable);
}
