package com.youthletter.domain.news.repository;

import com.youthletter.domain.news.entity.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    boolean existsByOriginalUrl(String originalUrl);

    /**
     * 임베딩 저장 (vector 타입은 JPA 자동 매핑 불가 -> 네이티브 쿼리)
     */
    @Modifying
    @Query(value = "UPDATE news SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    /**
     * 유사도 검색 (<=> = 코사인 유사도 연산자)
     */
    @Query(value = "SELECT * FROM news ORDER BY embedding <=> CAST(:queryVector AS vector) LIMIT :limit", nativeQuery = true)
    List<News> findSimilarNews(@Param("queryVector") String queryVector, @Param("limit") int limit);
}