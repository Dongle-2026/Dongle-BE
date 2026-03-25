package com.youthletter.domain.news.entity;

import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "summary_version", nullable = false)
    private Integer summaryVersion;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    // ── Temporal Versioning ──
    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;  // NULL이면 현재 유효 버전

    @Builder
    public NewsSummary(NewsArticle article, String summaryText,
                       Integer summaryVersion, String modelVersion) {
        this.article = article;
        this.summaryText = summaryText;
        this.summaryVersion = summaryVersion;
        this.modelVersion = modelVersion;
        this.validFrom = LocalDateTime.now();
        this.validTo = null;  // 생성 시점에는 현재 유효
    }

    /**
     * 새 버전이 생성될 때 기존 버전을 만료 처리
     */
    public void expire() {
        this.validTo = LocalDateTime.now();
    }
}
