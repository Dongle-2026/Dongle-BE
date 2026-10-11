package com.youthletter.domain.term.repository;

import com.youthletter.domain.term.entity.TermBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TermBookmarkRepository extends JpaRepository<TermBookmark, Long> {

    Optional<TermBookmark> findByUserIdAndTermId(Long userId, Long termId);

    @Query(value = "SELECT b FROM TermBookmark b JOIN FETCH b.term WHERE b.user.id = :userId ORDER BY b.createdAt DESC, b.id DESC",
           countQuery = "SELECT count(b) FROM TermBookmark b WHERE b.user.id = :userId")
    Page<TermBookmark> findByUserIdWithTerm(@Param("userId") Long userId, Pageable pageable);
}
