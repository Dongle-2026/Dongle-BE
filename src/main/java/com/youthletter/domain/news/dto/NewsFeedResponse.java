package com.youthletter.domain.news.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** App feed contract. Legacy page/size clients continue to receive Spring Page. */
public record NewsFeedResponse(List<Item> data, int status, Meta meta) {
    public record Meta(int page, int limit, boolean hasNext) {}
    public record Item(String id, String title, String description, String image, String category,
                       OffsetDateTime createdAt, boolean isSaved, List<Slide> slides) {}
    public record Slide(String id, String type, String image, String title, String description) {}
}
