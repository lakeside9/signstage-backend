package com.eformworks.signstage.backend.feature.support.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.support.dto.ContactInquiryDto;
import com.eformworks.signstage.backend.feature.support.service.ContactInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 화면의 고객 문의하기 — 로그인 없이 호출한다({@code SecurityConfig}가 {@code /api/public/**}를 연다).
 * 요청 제한은 {@code X-Forwarded-For}가 아닌 접속 주소로 건다 — 헤더는 요청자가 바꿔 제한을 피할 수 있다.
 */
@Tag(name = "Public", description = "로그인 없이 쓰는 공개 API")
@RestController
@RequestMapping("/api/public/contact-inquiries")
@RequiredArgsConstructor
public class PublicContactInquiryController {

    private final ContactInquiryService contactInquiryService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "고객 문의 접수", description = "같은 접속 주소에서 10분에 5건까지 받는다. 개인정보 수집·이용 동의가 필요하다.")
    @PostMapping
    public ApiResponse<Void> submit(
            @Valid @RequestBody ContactInquiryDto.Request.Submit request,
            HttpServletRequest httpRequest
    ) {
        contactInquiryService.submit(request, httpRequest.getRemoteAddr());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
