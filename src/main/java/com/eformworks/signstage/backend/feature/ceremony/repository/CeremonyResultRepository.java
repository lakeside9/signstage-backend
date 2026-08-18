package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyResultRepository extends JpaRepository<CeremonyResult, Long> {

    List<CeremonyResult> findAllByCeremonyEventId(Long ceremonyEventId);

    boolean existsByCeremonyEventId(Long ceremonyEventId);

    /**
     * 체크섬은 사실상 유일하지만 DB 유니크 제약은 없다 — 같은 원본 PDF에 같은 좌표로 스트로크를
     * 렌더링하면 서로 다른 {@link CeremonyResult}(예: CONTRACT/EXHIBITION)의 바이트가 완전히
     * 같아져 체크섬이 우연히 겹칠 수 있다(9라운드 이후 실제로 재현됨 —
     * signstage-docs business/ceremony-feature-migration-review.md §8.3). {@code findByChecksum}
     * (단일 결과 기대)이 이 경우 {@code NonUniqueResultException}으로 죽었던 자리라, 여러 건 중
     * 최신 것 하나를 고르도록 바꿨다 — 위변조 검증은 "이 파일이 진짜인가"만 확인하면 되므로
     * 어느 쪽이 반환되든 결과의 정확성에 문제가 없다.
     */
    Optional<CeremonyResult> findFirstByChecksumOrderByCreatedAtDesc(String checksum);
}
