package com.youthletter.domain.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.dto.NewsDetailResponse;
import com.youthletter.domain.news.service.AiJobService;
import com.youthletter.domain.news.service.NewsService;
import com.youthletter.domain.quiz.dto.NewsBodyResponse;
import com.youthletter.domain.quiz.dto.QuizAnswerRequest;
import com.youthletter.domain.quiz.dto.QuizAnswerResponse;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 뉴스 본문 학습(A10)과 퀴즈 제출(A11).
 * 퀴즈는 AI가 기사 문장 근거로 생성한다. 아직 없으면 본문 조회 시 생성 작업을 큐에 넣고 GENERATING을 알린다.
 * 재도전 허용, 꿀은 문제당 첫 정답 1회.
 */
@Service
@RequiredArgsConstructor
public class QuizService {
    private static final List<String> CHOICE_IDS = List.of("a", "b", "c", "d");
    private static final int SENTENCES_PER_SECTION = 3;
    private static final Map<String, String> TERM_CATEGORY = Map.of(
            "finance", "금융", "economy", "경제", "policy", "정책", "startup", "창업");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final NewsService newsService;
    private final AiJobService aiJobService;
    private final AiServiceClient aiServiceClient;
    private final UserRepository userRepository;
    private final HoneyService honeyService;

    @Value("${ai-jobs.enabled:true}")
    private boolean aiJobsEnabled;

    private record StoredQuiz(Long id, String question, List<String> choices) {}
    private record AnswerKey(Long articleId, int answerIndex, String explanation) {}

    @Transactional
    public NewsBodyResponse body(Long articleId, Long userId) {
        NewsDetailResponse detail = newsService.getNewsDetail(articleId);
        String intro = detail.summary() == null || detail.summary().isBlank() || detail.summary().startsWith("[STUB]")
                ? null : detail.summary();

        List<NewsBodyResponse.Term> terms = terms(detail, userId);
        List<StoredQuiz> stored = jdbc.query("SELECT id, question, choices FROM quizzes WHERE article_id=? ORDER BY quiz_order",
                (rs, i) -> new StoredQuiz(rs.getLong("id"), rs.getString("question"), parseChoices(rs.getString("choices"))), articleId);
        Map<Long, NewsBodyResponse.MyResult> mine = userId == null || stored.isEmpty() ? Map.of() : myResults(userId, articleId);
        List<NewsBodyResponse.Quiz> quizzes = stored.stream().map(q -> new NewsBodyResponse.Quiz(q.id(), q.question(),
                choices(q.choices()), HoneyService.QUIZ_CORRECT,
                userId == null ? null : mine.getOrDefault(q.id(), new NewsBodyResponse.MyResult(0, false, 0)))).toList();
        String status = stored.isEmpty() ? requestGeneration(articleId) : "READY";

        return new NewsBodyResponse(detail.id(), detail.title(), intro, detail.sourceName(), detail.publishedAt(),
                sections(detail.content()), terms, status, quizzes, List.of());
    }

    @Transactional
    public QuizAnswerResponse answer(Long userId, Long quizId, QuizAnswerRequest request) {
        int selected = selectedIndex(request);
        // 같은 사용자의 동시 제출을 직렬화한다(정답 판정과 지급이 한 번에 처리되도록).
        userRepository.findLockedById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        AnswerKey key = jdbc.query("SELECT article_id, answer_index, explanation FROM quizzes WHERE id=?",
                (rs, i) -> new AnswerKey(rs.getLong("article_id"), rs.getInt("answer_index"), rs.getString("explanation")), quizId)
                .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
        boolean correct = selected == key.answerIndex();
        jdbc.update("INSERT INTO quiz_attempts(user_id, quiz_id, selected_index, is_correct) VALUES (?, ?, ?, ?)",
                userId, quizId, selected, correct);
        int earned = correct ? honeyService.awardQuiz(userId, quizId, key.articleId()) : 0;
        return new QuizAnswerResponse(quizId, selected, correct, earned, correct && earned == 0,
                correct ? key.answerIndex() : null, correct ? key.explanation() : null);
    }

    private static int selectedIndex(QuizAnswerRequest request) {
        if (request == null || (request.selectedIndex() == null) == (request.choiceId() == null))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "selectedIndex 또는 choiceId 중 하나를 보내 주세요");
        int index = request.selectedIndex() != null ? request.selectedIndex() : CHOICE_IDS.indexOf(request.choiceId().strip().toLowerCase(Locale.ROOT));
        if (index < 0 || index > 3) throw new BusinessException(ErrorCode.INVALID_INPUT, "보기는 0~3(a~d)만 선택할 수 있습니다");
        return index;
    }

    /** 퀴즈가 없을 때: 생성 중이면 GENERATING, 최근 1시간 내 실패했거나 AI를 쓸 수 없으면 UNAVAILABLE. */
    private String requestGeneration(Long articleId) {
        if (!aiJobsEnabled || aiServiceClient.isStubMode()) return "UNAVAILABLE";
        String latest = jdbc.query("""
                SELECT CASE WHEN status IN ('QUEUED','RUNNING') THEN 'ACTIVE'
                            WHEN status = 'FAILED' AND updated_at > now() - interval '1 hour' THEN 'RECENT_FAILURE'
                            ELSE 'NONE' END
                FROM ai_jobs WHERE article_id=? AND kind='QUIZ' ORDER BY id DESC LIMIT 1
                """, (rs, i) -> rs.getString(1), articleId).stream().findFirst().orElse("NONE");
        if (latest.equals("RECENT_FAILURE")) return "UNAVAILABLE";
        if (latest.equals("NONE")) aiJobService.enqueue(articleId, AiJobService.Kind.QUIZ);
        return "GENERATING";
    }

    private Map<Long, NewsBodyResponse.MyResult> myResults(Long userId, Long articleId) {
        Map<Long, NewsBodyResponse.MyResult> out = new HashMap<>();
        jdbc.query("""
                SELECT q.id, count(a.id) AS attempts, coalesce(bool_or(a.is_correct), false) AS solved,
                       coalesce((SELECT sum(h.amount) FROM honey_ledger h WHERE h.user_id = ? AND h.quiz_id = q.id), 0) AS earned
                FROM quizzes q LEFT JOIN quiz_attempts a ON a.quiz_id = q.id AND a.user_id = ?
                WHERE q.article_id = ? GROUP BY q.id
                """, rs -> {
            out.put(rs.getLong("id"), new NewsBodyResponse.MyResult(rs.getInt("attempts"), rs.getBoolean("solved"), rs.getInt("earned")));
        }, userId, userId, articleId);
        return out;
    }

    private List<NewsBodyResponse.Term> terms(NewsDetailResponse detail, Long userId) {
        List<NewsDetailResponse.TermHighlightDto> highlights = detail.termHighlights() == null ? List.of() : detail.termHighlights();
        LinkedHashMap<Long, NewsDetailResponse.TermHighlightDto> unique = new LinkedHashMap<>();
        highlights.forEach(h -> unique.putIfAbsent(h.termId(), h));
        if (unique.isEmpty()) return List.of();
        Long[] ids = unique.keySet().toArray(Long[]::new);
        Map<Long, String> paths = new HashMap<>();
        jdbc.query("SELECT id, path::text AS path FROM financial_terms WHERE id = ANY(?)",
                rs -> { paths.put(rs.getLong("id"), rs.getString("path")); }, (Object) ids);
        Set<Long> saved = userId == null ? Set.of() : new HashSet<>(jdbc.queryForList(
                "SELECT term_id FROM term_bookmarks WHERE user_id=? AND term_id = ANY(?)", Long.class, userId, ids));
        return unique.values().stream().map(h -> new NewsBodyResponse.Term(h.termId(), h.termName(), h.simpleDefinition(),
                termCategory(paths.get(h.termId())), userId == null ? null : saved.contains(h.termId()))).toList();
    }

    private static String termCategory(String path) {
        if (path == null) return "일반";
        return TERM_CATEGORY.getOrDefault(path.split("\\.")[0], "일반");
    }

    /** 원문 줄바꿈 문단을 그대로 쓰고, 문단 구분이 없으면 3문장씩 묶는다. */
    static List<NewsBodyResponse.Section> sections(String content) {
        if (content == null || content.isBlank()) return List.of();
        List<String> paragraphs = Arrays.stream(content.split("\\n\\s*\\n|\\n")).map(String::strip).filter(p -> !p.isEmpty()).toList();
        if (paragraphs.size() <= 1) {
            List<String> sentences = QuizText.sentences(content);
            List<String> grouped = new ArrayList<>();
            for (int i = 0; i < sentences.size(); i += SENTENCES_PER_SECTION)
                grouped.add(String.join(" ", sentences.subList(i, Math.min(i + SENTENCES_PER_SECTION, sentences.size()))));
            paragraphs = grouped;
        }
        List<NewsBodyResponse.Section> out = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) out.add(new NewsBodyResponse.Section("s" + (i + 1), null, paragraphs.get(i)));
        return out;
    }

    private static List<NewsBodyResponse.Choice> choices(List<String> texts) {
        List<NewsBodyResponse.Choice> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) out.add(new NewsBodyResponse.Choice(CHOICE_IDS.get(i), texts.get(i)));
        return out;
    }

    private List<String> parseChoices(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
