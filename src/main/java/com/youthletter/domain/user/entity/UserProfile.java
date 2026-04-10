package com.youthletter.domain.user.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "employment_status", length = 20)
    private String employmentStatus;

    @Column(name = "annual_income")
    private Integer annualIncome;  // 만원 단위

    @Column(name = "is_married")
    private Boolean isMarried;

    @Column(name = "household_members")
    private Integer householdMembers;

    @Column(name = "education_level", length = 20)
    private String educationLevel;

    @Type(JsonType.class)
    @Column(name = "additional_criteria", columnDefinition = "jsonb")
    private String additionalCriteria;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public UserProfile(User user, String employmentStatus, Integer annualIncome,
                       Boolean isMarried, Integer householdMembers,
                       String educationLevel, String additionalCriteria) {
        this.user = user;
        this.employmentStatus = employmentStatus;
        this.annualIncome = annualIncome;
        this.isMarried = isMarried != null ? isMarried : false;
        this.householdMembers = householdMembers != null ? householdMembers : 1;
        this.educationLevel = educationLevel;
        this.additionalCriteria = additionalCriteria != null ? additionalCriteria : "{}";
    }

    public void update(String employmentStatus, Integer annualIncome,
                       Boolean isMarried, Integer householdMembers,
                       String educationLevel) {
        if (employmentStatus != null) this.employmentStatus = employmentStatus;
        if (annualIncome != null) this.annualIncome = annualIncome;
        if (isMarried != null) this.isMarried = isMarried;
        if (householdMembers != null) this.householdMembers = householdMembers;
        if (educationLevel != null) this.educationLevel = educationLevel;
    }
}
