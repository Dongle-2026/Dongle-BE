package com.youthletter.domain.cardnews.entity;

import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.global.util.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "card_news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardNews extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @Column(name = "total_cards", nullable = false)
    private int totalCards;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardNewsStatus status;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    public void recordGeneration(String model, String prompt) {
        this.modelVersion = model;
        this.promptVersion = prompt;
    }

    public void replaceCards(List<CardNewsCard> replacement) {
        cards.clear();
        cards.addAll(replacement);
        totalCards = replacement.size();
    }

    @OneToMany(mappedBy = "cardNews", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("cardOrder ASC")
    private List<CardNewsCard> cards = new ArrayList<>();

    @Builder
    public CardNews(NewsArticle article, int totalCards, CardNewsStatus status) {
        this.article = article;
        this.totalCards = totalCards;
        this.status = status;
    }

    public void publish() {
        this.status = CardNewsStatus.PUBLISHED;
    }

    public void updateTotalCards(int totalCards) {
        this.totalCards = totalCards;
    }
}
