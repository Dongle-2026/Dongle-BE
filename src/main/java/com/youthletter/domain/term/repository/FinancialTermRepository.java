package com.youthletter.domain.term.repository;

import com.youthletter.domain.term.entity.FinancialTerm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FinancialTermRepository extends JpaRepository<FinancialTerm, Long> {

    Optional<FinancialTerm> findByName(String name);

    /**
     * ltree 하위 용어 전체 조회
     * 예: path <@ 'finance.stock' → 주식, ETF, 배당 등
     */
    @Query(value = "SELECT * FROM financial_terms WHERE path <@ CAST(:parentPath AS ltree) ORDER BY path",
           nativeQuery = true)
    List<FinancialTerm> findByPathDescendants(@Param("parentPath") String parentPath);

    @Query(value = "SELECT * FROM financial_terms WHERE path <@ CAST(:parentPath AS ltree) ORDER BY path LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}",
           countQuery = "SELECT COUNT(*) FROM financial_terms WHERE path <@ CAST(:parentPath AS ltree)",
           nativeQuery = true)
    Page<FinancialTerm> findByPathDescendants(@Param("parentPath") String parentPath, Pageable pageable);

    /**
     * ltree 직접 하위 카테고리만 조회
     * 예: path ~ 'finance.*{1}' → stock, insurance, loan (1단계만)
     */
    @Query(value = "SELECT * FROM financial_terms WHERE path ~ CAST(:pattern AS lquery) ORDER BY path",
           nativeQuery = true)
    List<FinancialTerm> findByPathPattern(@Param("pattern") String pattern);

    /**
     * tsvector 기반 전문 검색
     * GIN 인덱스 idx_terms_search_gin 활용
     */
    @Query(value = "SELECT * FROM financial_terms " +
                   "WHERE search_vector @@ to_tsquery('simple', :query) " +
                   "ORDER BY ts_rank(search_vector, to_tsquery('simple', :query)) DESC " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<FinancialTerm> searchByText(@Param("query") String query, @Param("limit") int limit);

    /**
     * pg_trgm 기반 자동완성 (유사 문자열 매칭)
     * GIN 인덱스 idx_terms_name_trgm 활용
     */
    @Query(value = "SELECT * FROM financial_terms " +
                   "WHERE name % :query " +
                   "ORDER BY similarity(name, :query) DESC " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<FinancialTerm> findBySimilarName(@Param("query") String query, @Param("limit") int limit);

    /**
     * pgvector 코사인 유사도 검색 (RAG Agent용)
     * HNSW 인덱스 idx_terms_embedding_hnsw 활용
     */
    @Query(value = "SELECT * FROM financial_terms " +
                   "WHERE embedding IS NOT NULL " +
                   "ORDER BY embedding <=> CAST(:embedding AS vector) " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<FinancialTerm> findByEmbeddingSimilarity(@Param("embedding") String embedding,
                                                   @Param("limit") int limit);

    /**
     * 난이도별 용어 조회
     */
    List<FinancialTerm> findByDifficultyLevelLessThanEqualOrderByNameAsc(Integer maxLevel);
}
