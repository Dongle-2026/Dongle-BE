package com.youthletter.domain.guide.entity;

import com.youthletter.global.util.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guide extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** STOCK, INSURANCE 등 */
    @Column(name = "guide_type", nullable = false, length = 20)
    private String guideType;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "difficulty_level")
    private Integer difficultyLevel;

    // embedding은 네이티브 쿼리로 관리 (pgvector)

    @Builder
    public Guide(String guideType, String topic, String title,
                 String content, Integer difficultyLevel) {
        this.guideType = guideType;
        this.topic = topic;
        this.title = title;
        this.content = content;
        this.difficultyLevel = difficultyLevel != null ? difficultyLevel : 1;
    }
}
