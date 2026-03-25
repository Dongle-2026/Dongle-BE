package com.youthletter.domain.agent.entity;

import com.youthletter.domain.user.entity.User;
import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_feedback",
       uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer rating;  // 1~5

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Builder
    public AgentFeedback(ChatMessage message, User user, Integer rating, String comment) {
        this.message = message;
        this.user = user;
        this.rating = rating;
        this.comment = comment;
    }
}
