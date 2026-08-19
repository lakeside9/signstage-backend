package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateFieldRepository extends JpaRepository<TemplateField, Long> {

    List<TemplateField> findAllByTemplateId(Long templateId);

    /** 문서 양식 목록의 "서명란" 컬럼과 상태(설정 완료/필요) 계산에 쓴다. */
    long countByTemplateId(Long templateId);

    /** 문서 양식 삭제 시 먼저 지운다 — Template 삭제 전 FK 정리. */
    void deleteAllByTemplateId(Long templateId);

    /** 서명자 삭제 전 "서명란에 배정돼 있는지" 확인용. */
    boolean existsBySignerId(Long signerId);

    /** 서명자 수정 전 "시작/종료된 하위 행사의 서명란에 배정됐는지" 확인용 — 매핑된 템플릿을 봐야 해서 전체를 가져온다. */
    List<TemplateField> findAllBySignerId(Long signerId);
}
