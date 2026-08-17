package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.DocumentVerificationDto;
import com.eformworks.signstage.backend.feature.ceremony.service.DocumentVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 결과 PDF 위변조 검증. JWT 없이 누구나 파일을 업로드해 진위를 확인할 수 있다
 * (signstage-docs business/ceremony-feature-migration-review.md §2.5) —
 * {@code SecurityConfig}에서 {@code /api/verification/**}를 permitAll로 열어둔다.
 */
@Tag(name = "DocumentVerification", description = "결과 PDF 위변조 검증 API (인증 불필요)")
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class DocumentVerificationController {

    private final DocumentVerificationService documentVerificationService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "결과 PDF 진위 확인",
            description = "업로드한 파일의 체크섬을 결과물 저장 시점 체크섬과 대조한다. 일치하면 최소한의 설명 정보만 돌려준다."
    )
    @SecurityRequirements(value = {})
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentVerificationDto.Response.VerificationResult> verifyDocument(
            @RequestParam("file") MultipartFile file
    ) {
        DocumentVerificationDto.Response.VerificationResult response = documentVerificationService.verify(file);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
