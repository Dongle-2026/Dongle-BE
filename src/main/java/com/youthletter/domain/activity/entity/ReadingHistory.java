package com.youthletter.domain.activity.entity;

import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.user.entity.User;
import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reading_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @Column(name = "read_duration_seconds")
    private Integer readDurationSeconds;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    @Builder
    public ReadingHistory(User user, NewsArticle article, Integer readDurationSeconds) {
        this.user = user;
        this.article = article;
        this.readDurationSeconds = readDurationSeconds;
        this.readAt = LocalDateTime.now();
    }
}
