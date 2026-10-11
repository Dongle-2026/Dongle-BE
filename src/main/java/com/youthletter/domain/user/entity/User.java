package com.youthletter.domain.user.entity;

import com.youthletter.global.util.BaseTimeEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")  // soft delete: 조회 시 삭제된 사용자 자동 제외
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;  // OAuth 전용 사용자는 null

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    /** USER | ADMIN */
    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 10)
    private String gender;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(length = 50)
    private String occupation;

    @Column(name = "income_level", length = 20)
    private String incomeLevel;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String interests;  // JSON 문자열 ["주식", "보험"]

    /** 앱 프로필 소개글 (최대 100자) */
    @Column(length = 100)
    private String bio;

    /** 앱 기본 아바타 식별자 (사진이 없을 때 표시) */
    @Column(name = "avatar_id", length = 30)
    private String avatarId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(String email, String passwordHash, String name, String nickname,
                String role, LocalDate birthDate, String gender, String regionCode,
                String occupation, String incomeLevel, String interests) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.nickname = nickname;
        this.role = role != null ? role : "USER";
        this.birthDate = birthDate;
        this.gender = gender;
        this.regionCode = regionCode;
        this.occupation = occupation;
        this.incomeLevel = incomeLevel;
        this.interests = interests;
    }

    /**
     * 소프트 삭제 (회원 탈퇴)
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * 프로필 업데이트 (닉네임, 관심사 등)
     */
    public void updateProfile(String nickname, String regionCode, String occupation,
                              String incomeLevel, String interests) {
        if (nickname != null) this.nickname = nickname;
        if (regionCode != null) this.regionCode = regionCode;
        if (occupation != null) this.occupation = occupation;
        if (incomeLevel != null) this.incomeLevel = incomeLevel;
        if (interests != null) this.interests = interests;
    }

    /**
     * 앱 프로필 수정. null은 변경 없음, 빈 문자열 bio는 소개글 삭제.
     */
    public void updateAppProfile(String nickname, String bio, String avatarId) {
        if (nickname != null) this.nickname = nickname;
        if (bio != null) this.bio = bio.isEmpty() ? null : bio;
        if (avatarId != null) this.avatarId = avatarId;
    }

    /**
     * 정책 추천용 프로필 업데이트 (나이, 지역, 취업 여부, 소득 구간)
     */
    public void updatePolicyInfo(LocalDate birthDate, String regionCode,
                                 String occupation, String incomeLevel) {
        if (birthDate != null) this.birthDate = birthDate;
        if (regionCode != null) this.regionCode = regionCode;
        if (occupation != null) this.occupation = occupation;
        if (incomeLevel != null) this.incomeLevel = incomeLevel;
    }

    /**
     * 역할 변경 (USER ↔ ADMIN) — 관리자 전용 API에서만 호출
     */
    public void updateRole(String role) {
        this.role = role;
    }
}
