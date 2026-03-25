package com.youthletter.domain.user.entity;

import com.youthletter.global.util.BaseTimeEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

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

    @Column(columnDefinition = "jsonb")
    private String interests;  // JSON 문자열 ["주식", "보험"]

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(String email, String passwordHash, String name, String nickname,
                LocalDate birthDate, String gender, String regionCode,
                String occupation, String incomeLevel, String interests) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.nickname = nickname;
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
     * 프로필 업데이트
     */
    public void updateProfile(String nickname, String regionCode, String occupation,
                              String incomeLevel, String interests) {
        if (nickname != null) this.nickname = nickname;
        if (regionCode != null) this.regionCode = regionCode;
        if (occupation != null) this.occupation = occupation;
        if (incomeLevel != null) this.incomeLevel = incomeLevel;
        if (interests != null) this.interests = interests;
    }
}
