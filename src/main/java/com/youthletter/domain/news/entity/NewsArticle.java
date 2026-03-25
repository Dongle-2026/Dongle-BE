package com.youthletter.domain.news.entity;

import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_url", nullable = false, unique = true, length = 500)
    private String sourceUrl;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "crawled_at", nullable = false)
    private LocalDateTime crawledAt;

    @Builder
    public NewsArticle(String sourceUrl, String sourceName, String title,
                       String content, String category, LocalDateTime publishedAt) {
        this.sourceUrl = sourceUrl;
        this.sourceName = sourceName;
        this.title = title;
        this.content = content;
        this.category = category;
        this.publishedAt = publishedAt;
        this.crawledAt = LocalDateTime.now();
    }
}
