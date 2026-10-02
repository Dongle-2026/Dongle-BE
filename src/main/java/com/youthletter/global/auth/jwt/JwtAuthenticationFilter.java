package com.youthletter.global.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final com.youthletter.domain.user.repository.UserRepository users;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)
                && jwtTokenProvider.validateAccessToken(token)
                && !jwtTokenProvider.isBlacklisted(token)) {
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            // soft-deleted 계정과 변경된 권한을 매 요청 반영한다.
            users.findById(userId).ifPresent(user -> {
                var principal = new CustomUserPrincipal(user.getId(), user.getEmail());
                Authentication authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, "", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
