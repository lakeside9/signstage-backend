package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest;
import com.eformworks.signstage.backend.feature.ceremony.dto.SignerDto;
import com.eformworks.signstage.backend.feature.ceremony.service.SignerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * 서명자(Signer). Ceremony 직속이라 같은 행사의 TEST/MAIN 하위 행사가 명단을 공유한다
 * (signstage-docs business/ceremony-feature-migration-review.md 4.3절).
 */
@Tag(name = "Ceremony", description = "서명자(Signer) API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/ceremonies/{ceremonyId}/signers")
@RequiredArgsConstructor
public class SignerController {

    private final SignerService signerService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "서명자 등록", description = "플랜의 서명자 수 한도를 넘으면 거부된다.")
    @PostMapping
    public ApiResponse<SignerDto.Response.SignerSummary> createSigner(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody SignerDto.Request.CreateSigner request
    ) {
        SignerDto.Response.SignerSummary response =
                signerService.createSigner(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "서명자 일괄 업로드용 엑셀 양식 다운로드", description = "열 순서는 이름/소속/직위다.")
    @GetMapping("/excel-template")
    public ResponseEntity<byte[]> downloadExcelTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        byte[] workbook = signerService.generateExcelTemplate(organizationId, ceremonyId, currentUser.userId());
        String filename = URLEncoder.encode("서명자_업로드_양식.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(workbook);
    }

    @Operation(
            summary = "서명자 엑셀 일괄 업로드",
            description = "엑셀 양식(이름/소속/직위)으로 서명자를 한 번에 등록한다. 이름이 빈 행은 건너뛰고 결과에 표시한다. "
                    + "유효한 행 수가 플랜의 서명자 한도를 넘으면 아무것도 등록하지 않는다."
    )
    @PostMapping(value = "/excel-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SignerDto.Response.ExcelUploadResult> uploadSignersExcel(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @RequestParam("file") MultipartFile file
    ) {
        SignerDto.Response.ExcelUploadResult response =
                signerService.uploadSignersExcel(organizationId, ceremonyId, currentUser.userId(), file);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "서명자 목록 조회")
    @GetMapping
    public ApiResponse<List<SignerDto.Response.SignerSummary>> findSigners(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId
    ) {
        List<SignerDto.Response.SignerSummary> response =
                signerService.findSigners(organizationId, ceremonyId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "서명자 상세 조회")
    @GetMapping("/{signerId}")
    public ApiResponse<SignerDto.Response.SignerSummary> retrieveSigner(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long signerId
    ) {
        SignerDto.Response.SignerSummary response =
                signerService.retrieveSigner(organizationId, ceremonyId, signerId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "서명자 표시 순서 일괄 변경",
            description = "목록 화면의 위/아래 이동 버튼이 전체 배열을 원하는 순서로 다시 인덱싱해 통째로 보낸다."
    )
    @PutMapping("/display-orders")
    public ApiResponse<List<SignerDto.Response.SignerSummary>> updateDisplayOrders(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody DisplayOrderRequest.UpdateDisplayOrders request
    ) {
        List<SignerDto.Response.SignerSummary> response =
                signerService.updateDisplayOrders(organizationId, ceremonyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "서명자 수정", description = "이름/직책/소속/역할코드만 바꾼다. accessKey는 여기서 바꾸지 않는다.")
    @PutMapping("/{signerId}")
    public ApiResponse<SignerDto.Response.SignerSummary> updateSigner(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long signerId,
            @Valid @RequestBody SignerDto.Request.UpdateSigner request
    ) {
        SignerDto.Response.SignerSummary response =
                signerService.updateSigner(organizationId, ceremonyId, signerId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "서명자 삭제",
            description = "서명란에 배정됐거나 서명·감사 기록이 남아 있는 서명자는 삭제할 수 없다."
    )
    @DeleteMapping("/{signerId}")
    public ApiResponse<Void> deleteSigner(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @PathVariable Long signerId
    ) {
        signerService.deleteSigner(organizationId, ceremonyId, signerId, currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
