package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateDto;
import com.eformworks.signstage.backend.feature.ceremony.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(summary = "문서 양식 페이지 정보 조회", description = "총 페이지 수, 첫 페이지 크기(pt)를 반환한다.")
    @GetMapping("/{templateId}/info")
    public ApiResponse<TemplateDto.Response.TemplateInfo> retrieveTemplateInfo(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId
    ) {
        TemplateDto.Response.TemplateInfo response =
                templateService.retrieveTemplateInfo(organizationId, ceremonyId, templateId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "문서 양식 페이지 이미지 렌더링", description = "지정한 페이지를 PNG로 렌더링해 돌려준다.")
    @GetMapping("/{templateId}/pages/{pageIndex}")
    public ResponseEntity<byte[]> renderTemplatePage(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId,
            @PathVariable int pageIndex,
            @RequestParam(defaultValue = "1.5") float scale
    ) {
        byte[] png = templateService
                .renderTemplatePage(organizationId, ceremonyId, templateId, currentUser.userId(), pageIndex, scale);
        // TemplateService가 template/pageIndex/scale 조합으로 렌더링 결과를 캐시하므로, 같은
        // 사용자의 브라우저도 재요청 없이 재사용하게 한다. 조직별 접근 제어가 걸린 자원이라
        // 공유 캐시가 아닌 브라우저 캐시로만 제한한다(cachePrivate).
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(png);
    }

    @Operation(
            summary = "문서 양식 설정 완료",
            description = "서명란이 1개 이상이어야 한다. 완료되면 이후 서명란을 더 이상 바꿀 수 없다(되돌릴 수 없음)."
    )
    @PostMapping("/{templateId}/complete")
    public ApiResponse<TemplateDto.Response.TemplateSummary> completeTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId
    ) {
        TemplateDto.Response.TemplateSummary response =
                templateService.completeTemplate(organizationId, ceremonyId, templateId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "문서 양식 수정",
            description = "제목/문서유형만 바꾼다. PDF 파일 자체는 여기서 바꾸지 않는다(서명란 좌표가 깨지기 때문)."
    )
    @PutMapping("/{templateId}")
    public ApiResponse<TemplateDto.Response.TemplateSummary> updateTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId,
            @Valid @RequestBody TemplateDto.Request.UpdateTemplate request
    ) {
        TemplateDto.Response.TemplateSummary response = templateService
                .updateTemplate(organizationId, ceremonyId, templateId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "문서 양식 삭제",
            description = "이미 하위 행사에 매핑된 문서 양식은 삭제할 수 없다."
    )
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> deleteTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId
    ) {
        templateService.deleteTemplate(organizationId, ceremonyId, templateId, currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "문서 양식 복제",
            description = "원본 파일과 서명란(signer 매핑 포함)을 그대로 복사해 새 문서 양식을 만든다. 복제본 상태는 항상 DRAFT로 시작한다."
    )
    @PostMapping("/{templateId}/duplicate")
    public ApiResponse<TemplateDto.Response.TemplateSummary> duplicateTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId
    ) {
        TemplateDto.Response.TemplateSummary response =
                templateService.duplicateTemplate(organizationId, ceremonyId, templateId, currentUser.userId());
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
