package com.youthletter.domain.news.entity;

import com.youthletter.domain.user.entity.User;
import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "news_bookmarks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "article_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsBookmark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @Builder
    public NewsBookmark(User user, NewsArticle article) {
        this.user = user;
        this.article = article;
    }
}
