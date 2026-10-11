package com.youthletter.domain.term.entity;

import com.youthletter.domain.user.entity.User;
import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 용어 저장. 학습 이력(term_lookup_log)과 별개의 사용자 선택 상태. */
@Entity
@Table(name = "term_bookmarks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "term_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermBookmark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private FinancialTerm term;

    @Builder
    public TermBookmark(User user, FinancialTerm term) {
        this.user = user;
        this.term = term;
    }
}
