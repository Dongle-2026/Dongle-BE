package com.youthletter.domain.cardnews.service;

import com.youthletter.domain.cardnews.repository.CardNewsRepository;
import com.youthletter.domain.news.client.dto.AiCardResponse;
import com.youthletter.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CardNewsWriterTest {
    @Test void productionRejectsStubEvenWhenLocalIsAlsoActive() {
        var repository = mock(CardNewsRepository.class);
        var em = mock(EntityManager.class);
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local", "prod");
        var writer = new CardNewsWriter(repository, em, environment);
        assertThatThrownBy(() -> writer.save(1L, new AiCardResponse("stub", "stub", List.of())))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository, em);
    }
}
