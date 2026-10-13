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
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Access Token을 입력하세요 (Bearer 접두사 불필요)");

        return new OpenAPI()
                .info(new Info()
                        .title("유스레터 API")
                        .description("""
                                ## 청년 금융 AI 플랫폼 — Youth Letter Backend API

                                ### 인증 방식
                                - `POST /api/auth/login` 또는 카카오/구글 소셜 로그인으로 JWT 발급
                                - 보호된 API는 `Authorization: Bearer {accessToken}` 헤더 필수
                                - Swagger UI 우측 상단 **Authorize** 버튼에서 토큰 입력 후 테스트

                                ### 공통 응답 형식
                                ```json
                                { "status": 200, "message": "ok", "data": { ... } }
                                ```

                                ### 에러 코드 체계
                                | 범위 | 도메인 |
                                |------|--------|
                                | 40xx | 인증/인가 |
                                | 41xx | 사용자 |
                                | 42xx | 뉴스 |
                                | 43xx | 용어 |
                                | 44xx | 정책 |
                                | 45xx | AI Agent |
                                | 46xx | 소비 |
                                | 47xx | 주식 |
                                | 50xx | 서버 오류 |
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("정다은")
                                .email("ekdms8652@pusan.ac.kr")))
                .servers(List.of(
                        new Server().url("/").description("현재 Swagger를 연 서버")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme)
                        .addResponses("Unauthorized", unauthorizedResponse())
                        .addResponses("Forbidden", forbiddenResponse())
                        .addResponses("NotFound", notFoundResponse())
                        .addResponses("InternalError", internalErrorResponse()))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .paths(oAuth2Paths());
    }

    /**
     * 전역 공통 에러 응답 자동 주입 (401 / 403 / 500)
     */
    @Bean
    public OpenApiCustomizer globalResponseCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(op -> {
                    if (op.getResponses() == null) return;

                    // 이미 명시된 경우 덮어쓰지 않음
                    op.getResponses().putIfAbsent("401",
                            new ApiResponse().$ref("#/components/responses/Unauthorized"));
                    op.getResponses().putIfAbsent("500",
                            new ApiResponse().$ref("#/components/responses/InternalError"));
                })
        );
    }

    // ── 공통 에러 응답 컴포넌트 ────────────────────────────────────────────────

    private ApiResponse unauthorizedResponse() {
        return new ApiResponse()
                .description("인증 실패 — JWT 토큰 미전송, 만료 또는 블랙리스트 등록됨")
                .content(new Content().addMediaType("application/json",
                        new MediaType().example(Map.of("status", 4001, "message", "인증이 필요합니다"))));
    }

    private ApiResponse forbiddenResponse() {
        return new ApiResponse()
                .description("권한 없음 — ROLE_ADMIN 전용 API 접근 시도")
                .content(new Content().addMediaType("application/json",
                        new MediaType().example(Map.of("status", 4004, "message", "접근 권한이 없습니다"))));
    }

    private ApiResponse notFoundResponse() {
        return new ApiResponse()
                .description("리소스 없음")
                .content(new Content().addMediaType("application/json",
                        new MediaType().example(Map.of("status", 4200, "message", "리소스를 찾을 수 없습니다"))));
    }

    private ApiResponse internalErrorResponse() {
        return new ApiResponse()
                .description("서버 내부 오류")
                .content(new Content().addMediaType("application/json",
                        new MediaType().example(Map.of("status", 5000, "message", "서버 내부 오류가 발생했습니다"))));
    }

    // ── OAuth2 경로 문서 ───────────────────────────────────────────────────────

    private Paths oAuth2Paths() {
        ApiResponse redirectResponse = new ApiResponse()
                .description("소셜 로그인 페이지로 리다이렉트 → 인증 완료 후 `/auth/callback?accessToken=...&refreshToken=...` 으로 이동");

        return new Paths()
                .addPathItem("/oauth2/authorization/kakao", new PathItem()
                        .get(new Operation()
                                .tags(List.of("OAuth2 로그인"))
                                .summary("카카오 소셜 로그인")
                                .description("브라우저에서 직접 접근. 카카오 로그인 페이지로 리다이렉트됩니다.")
                                .operationId("kakaoLogin")
                                .security(List.of())
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
                                .description("""
                                        소셜 로그인 완료 후 자동 호출되는 콜백 URL. 직접 호출하지 않습니다.

                                        처리 결과:
                                        - 성공: `http://localhost:3000/auth/callback?accessToken=...&refreshToken=...` 리다이렉트
                                        - 실패: `http://localhost:3000/auth/callback?error=...` 리다이렉트
                                        """)
                                .operationId("oAuth2Callback")
                                .security(List.of())
                                .responses(new ApiResponses().addApiResponse("302", redirectResponse))));
    }
}
