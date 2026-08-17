package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateDto;
import com.eformworks.signstage.backend.feature.ceremony.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 문서 양식(Template). Ceremony 직속이다(signstage-docs
 * business/ceremony-feature-migration-review.md 4.2절). 업로드는 multipart라 JSON DTO 대신
 * {@code @RequestParam}으로 받는다.
 */
@Tag(name = "Ceremony", description = "문서 양식(Template) API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/ceremonies/{ceremonyId}/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "문서 양식 업로드",
            description = "PDF만 허용한다. 플랜의 템플릿 업로드 수 한도를 넘으면 거부된다."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TemplateDto.Response.TemplateSummary> uploadTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @RequestParam String title,
            @RequestParam String documentRole,
            @RequestParam("file") MultipartFile file
    ) {
        TemplateDto.Response.TemplateSummary response = templateService
                .uploadTemplate(organizationId, ceremonyId, currentUser.userId(), title, documentRole, file);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "문서 양식 목록 조회")
    @GetMapping
    public ApiResponse<List<TemplateDto.Response.TemplateSummary>> findTemplates(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<TemplateDto.Response.TemplateSummary> response =
                templateService.findTemplates(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "문서 양식 상세 조회")
    @GetMapping("/{templateId}")
    public ApiResponse<TemplateDto.Response.TemplateSummary> retrieveTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId
    ) {
        TemplateDto.Response.TemplateSummary response =
                templateService.retrieveTemplate(organizationId, ceremonyId, templateId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "문서 양식 원본 파일 다운로드")
    @GetMapping("/{templateId}/file")
    public ResponseEntity<Resource> downloadTemplateFile(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId
    ) {
        TemplateService.DownloadedTemplate downloaded =
                templateService.downloadTemplateFile(organizationId, ceremonyId, templateId, currentUser.userId());

        String encodedFilename = URLEncoder.encode(downloaded.originalFilename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(downloaded.resource());
    }
}
