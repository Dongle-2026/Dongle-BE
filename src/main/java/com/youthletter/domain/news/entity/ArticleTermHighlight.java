package com.youthletter.domain.news.entity;

import com.youthletter.domain.term.entity.FinancialTerm;
import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "article_term_highlights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleTermHighlight extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private FinancialTerm term;

    @Column(name = "position_start", nullable = false)
    private Integer positionStart;

    @Column(name = "position_end", nullable = false)
    private Integer positionEnd;

    @Builder
    public ArticleTermHighlight(NewsArticle article, FinancialTerm term,
                                Integer positionStart, Integer positionEnd) {
        this.article = article;
        this.term = term;
        this.positionStart = positionStart;
        this.positionEnd = positionEnd;
    }
}
