package com.youthletter.domain.news.service;
import com.youthletter.domain.news.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RecommendationServiceTest {
    @Test void limitsCategoryAndReturnsReason() {
        var repo = mock(RecommendationRepository.class);
        var a = candidate(1L, "LOAN"); var b = candidate(2L, "LOAN");
        var c = candidate(3L, "LOAN"); var d = candidate(4L, "POLICY");
        when(repo.recommend(9L)).thenReturn(List.of(a,b,c,d));
        var result = new RecommendationService(repo).recommend(9L);
        assertThat(result).extracting(RecommendationService.Recommendation::id).containsExactly(1L,2L,4L);
        assertThat(result).allMatch(r -> r.reasonCode().equals("LATEST_NEWS"));
    }
    private RecommendationRepository.Candidate candidate(Long id, String category) {
        var c = mock(RecommendationRepository.Candidate.class);
        when(c.getId()).thenReturn(id); when(c.getCategory()).thenReturn(category);
        when(c.getReasonCode()).thenReturn("LATEST_NEWS"); return c;
    }
}
