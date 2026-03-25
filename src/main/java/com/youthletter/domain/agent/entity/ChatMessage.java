package com.youthletter.domain.agent.entity;

import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    /**
     * USER, ASSISTANT, SYSTEM
     */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * RAG Agent가 호출한 도구 기록 (JSON)
     * 예: [{"tool": "news_search", "query": "ETF 수수료", "results": 3}]
     */
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private String toolCalls;

    /**
     * 응답의 출처 정보 (JSON)
     * 예: [{"type": "news", "id": 123, "title": "ETF 투자 가이드"}]
     */
    @Column(columnDefinition = "jsonb")
    private String sources;

    @Builder
    public ChatMessage(ChatSession session, String role, String content,
                       String toolCalls, String sources) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.sources = sources;
    }
}
