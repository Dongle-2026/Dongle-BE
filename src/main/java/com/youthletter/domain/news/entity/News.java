package com.youthletter.domain.news.entity;

import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class News extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "original_url", nullable = false, unique = true)
    private String originalUrl;

    @Column(length = 100)
    private String source;

    @Column(length = 100)
    private String category;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // embedding 컬럼은 vector(1536) 타입이라 JPA @Column으로 매핑하지 않음
    // 네이티브 쿼리(NewsRepository.updateEmbedding)로만 처리

    @Builder
    public News(String title, String content, String summary, String originalUrl,
                String source, String category, LocalDateTime publishedAt) {
        this.title = title;
        this.content = content;
        this.summary = summary;
        this.originalUrl = originalUrl;
        this.source = source;
        this.category = category;
        this.publishedAt = publishedAt;
    }

    public void updateSummary(String summary) {
        this.summary = summary;
    }
}