package com.youthletter.domain.policy.entity;

import com.youthletter.global.util.BaseTimeEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDate;

@Entity
@Table(name = "policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 100)
    private String organization;

    @Column(name = "region_code", length = 10)
    private String regionCode;  // NULL이면 전국

    @Column(name = "age_min")
    private Integer ageMin;

    @Column(name = "age_max")
    private Integer ageMax;

    @Column(name = "income_criteria", length = 50)
    private String incomeCriteria;

    @Type(JsonType.class)
    @Column(name = "occupation_criteria", columnDefinition = "jsonb")
    private String occupationCriteria;  // JSON 배열

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "application_url", length = 500)
    private String applicationUrl;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    // embedding은 네이티브 쿼리로 관리 (pgvector)

    @Builder
    public Policy(String title, String description, String summary,
                  String organization, String regionCode,
                  Integer ageMin, Integer ageMax,
                  String incomeCriteria, String occupationCriteria,
                  LocalDate startDate, LocalDate endDate,
                  String applicationUrl, String sourceUrl) {
        this.title = title;
        this.description = description;
        this.summary = summary;
        this.organization = organization;
        this.regionCode = regionCode;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.incomeCriteria = incomeCriteria;
        this.occupationCriteria = occupationCriteria;
        this.startDate = startDate;
        this.endDate = endDate;
        this.applicationUrl = applicationUrl;
        this.sourceUrl = sourceUrl;
    }

    /**
     * 정책이 특정 나이에 해당하는지 확인
     */
    public boolean isEligibleByAge(int age) {
        if (ageMin == null && ageMax == null) return true;
        if (ageMin != null && age < ageMin) return false;
        if (ageMax != null && age > ageMax) return false;
        return true;
    }

    /**
     * 정책이 마감 전인지 확인
     */
    public boolean isActive() {
        if (endDate == null) return true;
        return !LocalDate.now().isAfter(endDate);
    }
}
