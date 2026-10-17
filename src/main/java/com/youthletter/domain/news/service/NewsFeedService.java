package com.youthletter.domain.news.service;

import com.youthletter.domain.cardnews.entity.CardNewsStatus;
import com.youthletter.domain.cardnews.repository.CardNewsRepository;
import com.youthletter.domain.news.category.NewsCategory;
import com.youthletter.domain.news.dto.NewsFeedResponse;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NewsFeedService {
    private final NewsService newsService;
    private final CardNewsRepository cardNewsRepository;

    @Transactional(readOnly = true)
    public NewsFeedResponse feed(String category, String keyword, int page, int limit, Long userId) {
        if (page < 1 || limit < 1 || limit > 100)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page는 1 이상, limit는 1~100이어야 합니다");
        var result = newsService.getNewsList(category, keyword, null,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "publishedAt", "id")), userId);
        Map<Long, List<NewsFeedResponse.Slide>> slides = new HashMap<>();
        if (!result.isEmpty()) {
            var ids = result.getContent().stream().map(n -> n.id()).toList();
            for (var cards : cardNewsRepository.findFeedCards(ids, CardNewsStatus.PUBLISHED)) {
                var values = new ArrayList<NewsFeedResponse.Slide>();
                for (var card : cards.getCards()) {
                    boolean first = values.isEmpty();
                    String image = card.getImageUrl() != null ? card.getImageUrl()
                            : first ? cards.getArticle().getThumbnailUrl() : null;
                    values.add(new NewsFeedResponse.Slide(String.valueOf(card.getId()), first ? "image" : "content",
                            image, card.getTitle(), card.getBody()));
                }
                slides.put(cards.getArticle().getId(), values);
            }
        }
        var items = result.getContent().stream().map(n -> new NewsFeedResponse.Item(String.valueOf(n.id()),
                n.title(), n.summary(), n.image(), NewsCategory.resolve(n.category()).map(NewsCategory::id).orElse(n.category()),
                n.publishedAt() == null ? null : n.publishedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                Boolean.TRUE.equals(n.isSaved()), slides.getOrDefault(n.id(), List.of()))).toList();
        return new NewsFeedResponse(items, 200, new NewsFeedResponse.Meta(page, limit, result.hasNext()));
    }
}
