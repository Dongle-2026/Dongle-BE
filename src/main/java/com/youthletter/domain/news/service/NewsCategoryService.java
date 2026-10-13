package com.youthletter.domain.news.service;

import com.youthletter.domain.news.category.NewsCategoryClassifier;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsCategoryService {
    private static final int BATCH = 200;
    /** docker/postgres/03-sample-data.sql의 출처명 */
    static final String SAMPLE_SOURCE = "샘플 데이터";
    private final NewsArticleRepository articleRepository;

    /** 기존 기사(모두 '금융'으로 저장됨)를 키워드 규칙으로 재분류한다. 재실행해도 결과가 같다. */
    @Transactional
    public int reclassifyAll() {
        int updated = 0;
        Page<NewsArticle> page;
        int number = 0;
        do {
            page = articleRepository.findAll(PageRequest.of(number++, BATCH, Sort.by("id")));
            for (NewsArticle article : page) {
                if (SAMPLE_SOURCE.equals(article.getSourceName())) continue; // 로컬 샘플은 화면 시험용으로 카테고리를 직접 지정
                String category = NewsCategoryClassifier.classify(article.getTitle(), article.getContent()).displayName();
                if (!category.equals(article.getCategory())) {
                    article.changeCategory(category);
                    updated++;
                }
            }
        } while (page.hasNext());
        return updated;
    }
}
