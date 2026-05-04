package com.youthletter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // JWT Bearer 인증 스키마
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Access Token을 입력하세요 (Bearer 접두사 불필요)");

        return new OpenAPI()
                .info(new Info()
                        .title("유스레터 API")
                        .description("청년 금융 AI 플랫폼 - Youth Letter Backend API")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("정다은")
                                .email("daeun@example.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발"),
                        new Server().url("https://api.youthletter.com").description("운영 서버")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme))
                // 전역 Security 적용 (각 컨트롤러에서 @SecurityRequirement 없이도 기본 적용)
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .paths(oAuth2Paths());
    }

    private Paths oAuth2Paths() {
        ApiResponse redirectResponse = new ApiResponse()
                .description("소셜 로그인 페이지로 리다이렉트 → 인증 완료 후 login.html?accessToken=...&refreshToken=... 으로 이동");

        return new Paths()
                .addPathItem("/oauth2/authorization/kakao", new PathItem()
                        .get(new Operation()
                                .tags(List.of("OAuth2 로그인"))
                                .summary("카카오 소셜 로그인")
                                .description("브라우저에서 직접 접근. 카카오 로그인 페이지로 리다이렉트됩니다.")
                                .operationId("kakaoLogin")
                                .security(List.of())   // 인증 불필요
                                .responses(new ApiResponses().addApiResponse("302", redirectResponse))))
                .addPathItem("/oauth2/authorization/google", new PathItem()
                        .get(new Operation()
                                .tags(List.of("OAuth2 로그인"))
                                .summary("구글 소셜 로그인")
                                .description("브라우저에서 직접 접근. 구글 로그인 페이지로 리다이렉트됩니다.")
                                .operationId("googleLogin")
                                .security(List.of())
                                .responses(new ApiResponses().addApiResponse("302", redirectResponse))))
                .addPathItem("/login/oauth2/code/{registrationId}", new PathItem()
                        .get(new Operation()
                                .tags(List.of("OAuth2 로그인"))
                                .summary("OAuth2 콜백 (Spring Security 자동 처리)")
                                .description("소셜 로그인 완료 후 자동 호출되는 콜백 URL. 직접 호출하지 않습니다.\n\n" +
                                        "처리 결과:\n" +
                                        "- 성공: `login.html?accessToken=...&refreshToken=...` 리다이렉트\n" +
                                        "- 실패: `login.html?error=...` 리다이렉트")
                                .operationId("oAuth2Callback")
                                .security(List.of())
                                .responses(new ApiResponses().addApiResponse("302", redirectResponse))));
    }
}
