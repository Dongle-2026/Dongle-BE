package com.youthletter.domain.activity.entity;

import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.term.entity.FinancialTerm;
import com.youthletter.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "term_lookup_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermLookupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private FinancialTerm term;

    /** 어떤 뉴스에서 클릭했는지 (용어 사전에서 직접 검색하면 null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    private NewsArticle article;

    @Column(name = "looked_up_at", nullable = false)
    private LocalDateTime lookedUpAt;

    @Builder
    public TermLookupLog(User user, FinancialTerm term, NewsArticle article) {
        this.user = user;
        this.term = term;
        this.article = article;
        this.lookedUpAt = LocalDateTime.now();
    }
}
