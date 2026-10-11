package com.youthletter.domain.cardnews.repository;

import com.youthletter.domain.cardnews.entity.CardNews;
import com.youthletter.domain.cardnews.entity.CardNewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CardNewsRepository extends JpaRepository<CardNews, Long> {

    Optional<CardNews> findByArticleId(Long articleId);

    boolean existsByArticleId(Long articleId);

    @Query("SELECT cn FROM CardNews cn JOIN FETCH cn.article WHERE cn.status = :status ORDER BY cn.createdAt DESC")
    Page<CardNews> findAllByStatus(CardNewsStatus status, Pageable pageable);
}
