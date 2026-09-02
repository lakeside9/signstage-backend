package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateFieldDto;
import com.eformworks.signstage.backend.feature.ceremony.service.TemplateFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 페이지 내 서명란 좌표(TemplateField). */
@Tag(name = "Ceremony", description = "서명란(TemplateField) API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/ceremonies/{ceremonyId}/templates/{templateId}/fields")
@RequiredArgsConstructor
public class TemplateFieldController {

    private final TemplateFieldService templateFieldService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "서명란 등록", description = "좌표는 0~1 비율이다. signerId는 생략할 수 있다.")
    @PostMapping
    public ApiResponse<TemplateFieldDto.Response.TemplateFieldSummary> createTemplateField(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId,
            @Valid @RequestBody TemplateFieldDto.Request.CreateTemplateField request
    ) {
        TemplateFieldDto.Response.TemplateFieldSummary response = templateFieldService
                .createTemplateField(organizationId, ceremonyId, templateId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "서명란 일괄 저장",
            description = "현재 전체 서명란 배열을 통째로 보낸다(diff 없음) — 기존 서명란을 전부 지우고 다시 채운다. "
                    + "설정 완료(COMPLETED)된 문서 양식은 호출할 수 없다."
    )
    @PutMapping
    public ApiResponse<List<TemplateFieldDto.Response.TemplateFieldSummary>> setTemplateFields(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId,
            @Valid @RequestBody TemplateFieldDto.Request.SetFields request
    ) {
        List<TemplateFieldDto.Response.TemplateFieldSummary> response = templateFieldService
                .setFields(organizationId, ceremonyId, templateId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "서명란 복제",
            description = "같은 협약 내 같은 문서 역할(documentRole)의 다른 문서에서 서명란 배치를 통째로 가져와 이 문서의 기존 서명란을 교체한다."
    )
    @PostMapping("/clone-from/{sourceTemplateId}")
    public ApiResponse<List<TemplateFieldDto.Response.TemplateFieldSummary>> cloneTemplateFields(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId,
            @PathVariable Long sourceTemplateId
    ) {
        List<TemplateFieldDto.Response.TemplateFieldSummary> response = templateFieldService
                .cloneFields(organizationId, ceremonyId, templateId, sourceTemplateId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "서명란 목록 조회")
    @GetMapping
    public ApiResponse<List<TemplateFieldDto.Response.TemplateFieldSummary>> findTemplateFields(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long templateId
    ) {
        List<TemplateFieldDto.Response.TemplateFieldSummary> response = templateFieldService
                .findTemplateFields(organizationId, ceremonyId, templateId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
