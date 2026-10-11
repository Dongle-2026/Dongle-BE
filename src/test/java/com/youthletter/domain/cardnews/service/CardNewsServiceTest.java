package com.youthletter.domain.cardnews.service;
import com.youthletter.domain.cardnews.repository.*;
import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.global.exception.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CardNewsServiceTest {
    @Test void failedGenerationNeverWritesOrDeletesExistingCards() {
        var repo = mock(CardNewsRepository.class);
        var articles = mock(NewsArticleRepository.class);
        var ai = mock(AiServiceClient.class);
        var writer = mock(CardNewsWriter.class);
        var service = new CardNewsService(repo, mock(CardNewsCardRepository.class), articles,
                mock(CardNewsStubGenerator.class), ai, new CardNewsValidator(), writer);
        when(articles.findById(1L)).thenReturn(Optional.of(NewsArticle.builder().title("제목").content("본문").build()));
        when(ai.generateCards(any())).thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR));
        assertThatThrownBy(() -> service.generate(1L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repo, writer);
    }
    @Test void stubModeSavesStubCardsWithoutCallingAi() {
        var articles = mock(NewsArticleRepository.class);
        var ai = mock(AiServiceClient.class);
        var writer = mock(CardNewsWriter.class);
        var service = new CardNewsService(mock(CardNewsRepository.class), mock(CardNewsCardRepository.class), articles,
                new CardNewsStubGenerator(), ai, new CardNewsValidator(), writer);
        when(articles.findById(1L)).thenReturn(Optional.of(NewsArticle.builder().title("제목").content("본문").category("YOUTH").build()));
        when(ai.isStubMode()).thenReturn(true);
        service.generate(1L);
        verify(ai, never()).generateCards(any());
        verify(writer).save(eq(1L), argThat(r -> "stub".equals(r.modelVersion()) && r.cards().size() == 7));
    }
}
