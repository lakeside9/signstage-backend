package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.identity.entity.UserHistory;
import java.util.List;

public interface UserHistoryRepository extends AppendOnlyRepository<UserHistory, Long> {

    /** 최신순 — 가입 시점 1건 + 이후 정보가 바뀔 때마다 1건씩(회원 본인·관리자 구분 없음). */
    List<UserHistory> findAllByUserIdOrderByCreatedAtDesc(Long userId);

}
