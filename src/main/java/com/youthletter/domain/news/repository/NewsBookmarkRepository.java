package com.youthletter.domain.news.repository;

import com.youthletter.domain.news.entity.NewsBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NewsBookmarkRepository extends JpaRepository<NewsBookmark, Long> {

    Optional<NewsBookmark> findByUserIdAndArticleId(Long userId, Long articleId);

    boolean existsByUserIdAndArticleId(Long userId, Long articleId);

    @Query("SELECT b.article.id FROM NewsBookmark b WHERE b.user.id = :userId AND b.article.id IN :articleIds")
    java.util.List<Long> findSavedArticleIds(@Param("userId") Long userId, @Param("articleIds") java.util.Collection<Long> articleIds);

    @Query("SELECT b FROM NewsBookmark b JOIN FETCH b.article WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    Page<NewsBookmark> findByUserIdWithArticle(@Param("userId") Long userId, Pageable pageable);
}
