package com.youthletter.domain.spending.entity;

import com.youthletter.domain.user.entity.User;
import com.youthletter.global.util.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDate;

@Entity
@Table(name = "spending_insights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpendingInsight extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "analysis_start", nullable = false)
    private LocalDate analysisStart;

    @Column(name = "analysis_end", nullable = false)
    private LocalDate analysisEnd;

    @Column(name = "insight_text", nullable = false, columnDefinition = "TEXT")
    private String insightText;

    @Type(JsonType.class)
    @Column(name = "insight_data", nullable = false, columnDefinition = "jsonb")
    private String insightData;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Builder
    public SpendingInsight(User user, LocalDate analysisStart, LocalDate analysisEnd,
                           String insightText, String insightData, String modelVersion) {
        this.user = user;
        this.analysisStart = analysisStart;
        this.analysisEnd = analysisEnd;
        this.insightText = insightText;
        this.insightData = insightData;
        this.modelVersion = modelVersion;
    }
}
