package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.feature.identity.entity.UserHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {

    /** 최신순 — 가입 시점 1건 + 이후 정보가 바뀔 때마다 1건씩(회원 본인·관리자 구분 없음). */
    List<UserHistory> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 강제 탈퇴({@code User#withdraw}) 시 이 회원의 기존 이력 행에 남아 있는 이름/이메일/전화번호를
     * 지운다 — 살아있는 {@code users} 행만 마스킹하고 이력은 그대로 두면, 이력 테이블이 삭제된
     * PII를 조회할 수 있는 우회로가 되어 탈퇴 마스킹의 취지가 무의미해진다({@link UserHistory}
     * 클래스 설명 참고). append-only 원칙의 유일한 예외다 — "새 행을 추가하지 않고 과거 행을
     * 고친다"는 점에서 다르지만, 목적이 PII 삭제 그 자체라 예외를 둘 수밖에 없다.
     */
    @Modifying
    @Query("UPDATE UserHistory h SET h.name = :maskedName, h.email = null, h.phone = null WHERE h.user.id = :userId")
    void maskPiiForUser(@Param("userId") Long userId, @Param("maskedName") String maskedName);
}
