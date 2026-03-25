package com.youthletter.domain.term.entity;

import com.youthletter.global.util.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "financial_terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialTerm extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * ltree 타입은 JPA 기본 타입이 아니므로 String으로 매핑.
     * 네이티브 쿼리에서 ltree 연산자(<@, @>, ~)를 직접 사용.
     * 예: "finance.stock.etf"
     */
    @Column(nullable = false, columnDefinition = "ltree")
    private String path;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String definition;

    @Column(name = "simple_definition", nullable = false, columnDefinition = "TEXT")
    private String simpleDefinition;

    /**
     * tsvector/vector 타입은 JPA로 직접 매핑하지 않음.
     * - search_vector: 트리거가 자동 생성 (init.sql)
     * - embedding: AI Service에서 네이티브 쿼리로 INSERT/UPDATE
     * 읽기도 네이티브 쿼리(pgvector 코사인 유사도)를 사용하므로
     * 엔티티 필드로 가지고 있을 필요 없음.
     */

    @Column(name = "difficulty_level")
    private Integer difficultyLevel;

    @Builder
    public FinancialTerm(String name, String path, String definition,
                         String simpleDefinition, Integer difficultyLevel) {
        this.name = name;
        this.path = path;
        this.definition = definition;
        this.simpleDefinition = simpleDefinition;
        this.difficultyLevel = difficultyLevel != null ? difficultyLevel : 1;
    }

    public void updateDefinitions(String definition, String simpleDefinition) {
        if (definition != null) this.definition = definition;
        if (simpleDefinition != null) this.simpleDefinition = simpleDefinition;
    }
}
