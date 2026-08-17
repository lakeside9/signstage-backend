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
}
