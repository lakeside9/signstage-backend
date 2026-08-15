package com.eformworks.signstage.backend.core.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("SignStage API Document")
                        .description("""
                                SignStage 백엔드 API 문서입니다.

                                각 API 우측의 자물쇠 아이콘으로 인증 필요 여부를 확인할 수 있습니다.
                                열린 자물쇠(또는 아이콘 없음)는 인증 없이 호출 가능, 잠긴 자물쇠는
                                Authorize 버튼으로 Bearer 토큰을 넣어야 호출됩니다.""")
                        .version("0.0.1"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
