package com.youthletter.config;

import com.youthletter.global.auth.jwt.JwtAuthenticationFilter;
import com.youthletter.global.auth.oauth.CustomOAuth2UserService;
import com.youthletter.global.auth.oauth.OAuth2AuthenticationFailureHandler;
import com.youthletter.global.auth.oauth.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 비활성화 (JWT 기반 stateless이므로)
            .csrf(AbstractHttpConfigurer::disable)

            // 세션 미사용 (JWT 기반)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CORS 설정
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 엔드포인트별 접근 권한
            .authorizeHttpRequests(auth -> auth
                // 인증 없이 접근 가능
                .requestMatchers(
                    "/api/auth/**",           // 로그인, 토큰 재발급
                    "/api/news",              // 뉴스 목록 (비로그인 허용)
                    "/api/news/today",        // 오늘 브리핑 (비로그인 허용, 로그인 시 개인화)
                    "/api/news/{id}",         // 뉴스 상세 (비로그인 허용)
                    "/api/terms",             // 용어 목록 (비로그인 허용)
                    "/api/terms/search",      // 용어 검색 (비로그인 허용)
                    "/api/terms/autocomplete",// 자동완성 (비로그인 허용)
                    "/api/terms/category",    // 카테고리별 용어 (비로그인 허용)
                    "/api/terms/level",       // 난이도별 용어 (비로그인 허용)
                    "/api/terms/{id}",        // 용어 상세 (비로그인 허용)
                    "/api/guides/**",         // 가이드 (비로그인 허용)
                    "/oauth2/**",             // OAuth2 리다이렉트
                    "/login/oauth2/**",       // OAuth2 콜백
                    "/swagger-ui/**",         // Swagger UI
                    "/v3/api-docs/**",        // OpenAPI 명세
                    "/actuator/health",       // 헬스체크
                    "/*.html",                // 정적 테스트 페이지
                    "/favicon.ico"
                ).permitAll()
                // 관리자 API — ADMIN 역할 필요
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )

            // OAuth2 로그인 설정
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )

            // JWT 필터 등록 (UsernamePasswordAuthenticationFilter 앞에)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        var config = new org.springframework.web.cors.CorsConfiguration();
        config.addAllowedOrigin("http://localhost:3001");  // Next.js 프론트
        config.addAllowedOrigin("http://localhost:3000");  // 개발용
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/oauth2/**", config);
        source.registerCorsConfiguration("/login/oauth2/**", config);
        return source;
    }
}