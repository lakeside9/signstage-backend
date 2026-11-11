package com.eformworks.signstage.backend.feature.identity.port;

import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.model.UserMembership;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 사용자의 조직 소속(멤버십)을 조회·정리하는 계약. 구현은 {@code feature.organization}이 제공한다 —
 * identity가 organization의 Entity/Repository를 직접 참조하지 않도록 의존 방향을 뒤집는다
 * (backend-coding-convention.md 3.4절, backend-package-structure-consistency-review.md 11.9절).
 */
public interface UserMembershipPort {

    /** 데모 조직에 VIEWER로 활성 소속돼 있는지. 로그인 응답의 데모 레이아웃 판정에 쓴다. */
    boolean isDemoViewer(Long userId);

    /** 활성 소속 조직이 하나도 없는 사용자 검색. 검색어는 null이면 조건에서 뺀다. */
    Page<User> searchUsersWithoutOrganization(String loginId, String name, String email, Pageable pageable);

    /** 제거(REMOVED)되지 않은 소속 목록. */
    List<UserMembership> findMemberships(Long userId);

    /**
     * 회원 탈퇴에 맞춰 활성 소속을 모두 해제한다. 어떤 조직의 마지막 활성 OWNER면 아무것도 바꾸지 않고
     * {@code ORGANIZATION_LAST_OWNER_REQUIRED}로 거부한다.
     */
    void removeMembershipsForWithdrawal(Long userId);
}
