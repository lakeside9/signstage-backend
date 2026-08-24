package com.eformworks.signstage.backend.feature.ceremony.port;

import com.eformworks.signstage.backend.feature.ceremony.model.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 행사 문서(Template) 파일 저장 계약. 구현은 {@code integration.storage}가 제공한다
 * (backend-coding-convention.md Port/Integration 패턴). 지금은 로컬 디스크 구현만 있고,
 * 나중에 S3 등으로 교체해도 이 인터페이스를 쓰는 쪽(TemplateService)은 바뀌지 않는다.
 */
public interface DocumentStoragePort {

    StoredFile store(String directory, MultipartFile file);

    /** 업로드 파일이 아니라 메모리상의 바이트 배열(예: 생성된 결과 PDF)을 저장할 때 쓴다. */
    StoredFile store(String directory, String filename, byte[] content);

    Resource loadAsResource(String storageKey);

    /** 문서 양식 삭제 시 쓴다. 이미 없는 파일이어도 에러를 내지 않는다(삭제는 멱등이어야 한다). */
    void delete(String storageKey);

    /**
     * storageKey가 그대로 저장 경로가 되는 결정적(deterministic) 저장 — {@link #store}와 달리
     * 파일명을 임의로(UUID) 새로 짓지 않는다. {@code TemplateService}의 페이지 이미지 캐시처럼
     * 같은 key로 다시 저장을 요청하면 항상 같은 파일을 덮어쓰는 캐시 용도에 쓴다.
     */
    void storeAt(String storageKey, byte[] content);

    /** storageKey에 해당하는 파일이 이미 있는지. 페이지 이미지 캐시 조회에 쓴다. */
    boolean exists(String storageKey);
}
