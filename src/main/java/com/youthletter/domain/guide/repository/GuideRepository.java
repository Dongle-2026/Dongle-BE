package com.youthletter.domain.guide.repository;

import com.youthletter.domain.guide.entity.Guide;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuideRepository extends JpaRepository<Guide, Long> {

    Page<Guide> findByGuideTypeOrderByDifficultyLevelAsc(String guideType, Pageable pageable);

    @Query(value = "SELECT * FROM guides " +
                   "WHERE embedding IS NOT NULL " +
                   "ORDER BY embedding <=> CAST(:embedding AS vector) " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<Guide> findByEmbeddingSimilarity(@Param("embedding") String embedding,
                                          @Param("limit") int limit);
}
