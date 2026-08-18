package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.SignerDto;
import com.eformworks.signstage.backend.feature.ceremony.service.SignerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
