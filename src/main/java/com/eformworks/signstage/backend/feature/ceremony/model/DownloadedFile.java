package com.eformworks.signstage.backend.feature.ceremony.model;

import org.springframework.core.io.Resource;

/**
 * 저장소에서 읽어 온 파일과 원본 파일명. 서비스가 다운로드 컨트롤러에 넘기고, 컨트롤러는
 * 원본 파일명으로 {@code Content-Disposition} 헤더를 만든다(문서 양식·행사 결과 PDF 공용).
 */
public record DownloadedFile(Resource resource, String originalFilename) {
}
