package com.youthletter.domain.user.controller;

import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자 전용 유저 관리 API.
 * SecurityConfig에서 /api/admin/** → hasRole("ADMIN") 으로 보호됨.
 */
@Tag(name = "Admin - User", description = "관리자 전용 유저 관리 API")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;

    @Operation(summary = "이메일로 유저 조회", description = "유저 ID 확인 후 역할 변경에 사용")
    @GetMapping
    public GlobalResponse<Map<String, Object>> findByEmail(
            @Parameter(description = "조회할 이메일", example = "friend@example.com")
            @RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return GlobalResponse.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        ));
    }

    @Operation(
        summary = "유저 역할 변경",
        description = "role 값: `USER` 또는 `ADMIN`. 관리자 계정 추가/회수에 사용."
    )
    @PatchMapping("/{userId}/role")
    public GlobalResponse<Map<String, Object>> updateRole(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {

        String newRole = body.get("role");
        if (!"USER".equals(newRole) && !"ADMIN".equals(newRole)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateRole(newRole);
        userRepository.save(user);

        return GlobalResponse.ok(
                newRole.equals("ADMIN") ? "관리자 권한을 부여했습니다" : "일반 유저로 변경했습니다",
                Map.of("id", userId, "email", user.getEmail(), "role", newRole)
        );
    }

    @Operation(summary = "전체 관리자 목록 조회")
    @GetMapping("/admins")
    public GlobalResponse<?> listAdmins() {
        return GlobalResponse.ok(
                userRepository.findAll().stream()
                        .filter(u -> "ADMIN".equals(u.getRole()))
                        .map(u -> Map.of("id", u.getId(), "email", u.getEmail(), "nickname", u.getNickname()))
                        .toList()
        );
    }
}
