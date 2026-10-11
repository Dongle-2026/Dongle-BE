package com.youthletter.domain.quiz.service;

import com.youthletter.domain.news.client.dto.AiQuizResponse;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QuizWriterShuffleTest {
    @Test void shuffleKeepsAnswerAndSpreadsPositions() {
        var item = new AiQuizResponse.Item("q", List.of("a", "b", "c", "d"), 1, "e", List.of(1));
        Set<Integer> positions = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            var s = QuizWriter.shuffled(item);
            assertThat(s.choices().get(s.answerIndex())).isEqualTo("b");
            assertThat(s.choices()).containsExactlyInAnyOrder("a", "b", "c", "d");
            positions.add(s.answerIndex());
        }
        assertThat(positions).hasSize(4);
    }
}
