package com.youthletter.domain.news.service;

import com.youthletter.domain.news.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRepository repository;
    public record Recommendation(Long id, String title, String category, Double score, String reasonCode) {}
    @Transactional(readOnly = true)
    public List<Recommendation> recommend(Long userId) {
        Map<String, Integer> counts = new HashMap<>();
        List<Recommendation> result = new ArrayList<>();
        for (var item : repository.recommend(userId)) {
            if (counts.getOrDefault(item.getCategory(), 0) >= 2) continue;
            result.add(new Recommendation(item.getId(), item.getTitle(), item.getCategory(), item.getScore(), item.getReasonCode()));
            counts.merge(item.getCategory(), 1, Integer::sum);
            if (result.size() == 3) break;
        }
        return result;
    }
}
