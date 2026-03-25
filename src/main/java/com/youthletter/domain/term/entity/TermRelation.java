package com.youthletter.domain.term.entity;

import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "term_relations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"term_id", "related_term_id", "relation_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermRelation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private FinancialTerm term;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_term_id", nullable = false)
    private FinancialTerm relatedTerm;

    /** SYNONYM, PARENT, SEE_ALSO, OPPOSITE */
    @Column(name = "relation_type", nullable = false, length = 20)
    private String relationType;

    @Builder
    public TermRelation(FinancialTerm term, FinancialTerm relatedTerm, String relationType) {
        this.term = term;
        this.relatedTerm = relatedTerm;
        this.relationType = relationType;
    }
}
