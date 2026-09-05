package com.eformworks.signstage.backend.core.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * 애플리케이션의 이력/감사 로그 저장소가 공통으로 노출하는 최소 API.
 *
 * <p>신규 행 저장과 조회만 허용하고 {@code delete*}, {@code flush} 같은 변경 API는 노출하지
 * 않는다. 엔티티의 {@code @Immutable}과 함께 일반 업무 코드에서 기존 이력 행을 수정하거나
 * 삭제하는 경로를 막는다. 개인정보 보존기간 만료에 따른 정리는 이 저장소를 사용하지 않는
 * 별도 배치 프로세스의 책임이다.
 */
@NoRepositoryBean
public interface AppendOnlyRepository<T, ID> extends Repository<T, ID> {

    <S extends T> S save(S entity);

    <S extends T> List<S> saveAll(Iterable<S> entities);

    Optional<T> findById(ID id);

    List<T> findAll();

    Page<T> findAll(Pageable pageable);
}
