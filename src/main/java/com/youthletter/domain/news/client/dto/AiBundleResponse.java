package com.youthletter.domain.news.client.dto;

public record AiBundleResponse(AiProcessResponse article, AiCardResponse cards,
                               AiQuizResponse quiz, String modelVersion) {}
