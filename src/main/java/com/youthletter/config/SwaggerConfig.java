package com.youthletter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
