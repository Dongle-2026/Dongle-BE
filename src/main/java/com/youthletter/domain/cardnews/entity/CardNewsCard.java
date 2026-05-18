package com.youthletter.domain.cardnews.entity;

import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "card_news_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardNewsCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_news_id", nullable = false)
    private CardNews cardNews;

    @Column(name = "card_order", nullable = false)
    private int cardOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 10)
    private CardTemplateType templateType;

    @Column(length = 50)
    private String title;

    @Column(length = 200)
    private String body;

    @Column(name = "sub_body", length = 200)
    private String subBody;

    @Column(length = 100)
    private String highlight;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Builder
    public CardNewsCard(CardNews cardNews, int cardOrder, CardTemplateType templateType,
                        String title, String body, String subBody, String highlight) {
        this.cardNews = cardNews;
        this.cardOrder = cardOrder;
        this.templateType = templateType;
        this.title = title;
        this.body = body;
        this.subBody = subBody;
        this.highlight = highlight;
    }
}
