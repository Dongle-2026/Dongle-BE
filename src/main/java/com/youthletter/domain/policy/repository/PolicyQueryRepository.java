package com.youthletter.domain.policy.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.youthletter.domain.policy.entity.Policy;
import com.youthletter.domain.policy.entity.QPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * QueryDSL 동적 쿼리 기반 정책 조회
 */
@Repository
@RequiredArgsConstructor
public class PolicyQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 정책 목록 조회 — 지역 필터 + 활성 정책만
     * regionCode == null → 전국 정책만
     * regionCode 있음 → 전국(null) OR 해당 지역 정책
     */
    public Page<Policy> findActivePolicies(String regionCode, Pageable pageable) {
        QPolicy policy = QPolicy.policy;
        LocalDate today = LocalDate.now();

        BooleanBuilder where = new BooleanBuilder();
        where.and(policy.endDate.isNull().or(policy.endDate.goe(today)));

        if (StringUtils.hasText(regionCode)) {
            where.and(policy.regionCode.isNull().or(policy.regionCode.eq(regionCode)));
        }

        List<Policy> content = queryFactory
                .selectFrom(policy)
                .where(where)
                .orderBy(policy.endDate.asc().nullsLast())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(policy.count())
                .from(policy)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /**
     * 프로필 기반 맞춤 추천 — 나이 + 지역 + 소득 기준 동적 쿼리
     */
    public Page<Policy> findByProfile(int age, String regionCode,
                                      String incomeCriteria, Pageable pageable) {
        QPolicy policy = QPolicy.policy;
        LocalDate today = LocalDate.now();

        BooleanBuilder where = new BooleanBuilder();
        where.and(policy.endDate.isNull().or(policy.endDate.goe(today)));
        where.and(policy.ageMin.isNull().or(policy.ageMin.loe(age)));
        where.and(policy.ageMax.isNull().or(policy.ageMax.goe(age)));

        if (StringUtils.hasText(regionCode)) {
            where.and(policy.regionCode.isNull().or(policy.regionCode.eq(regionCode)));
        }
        if (StringUtils.hasText(incomeCriteria)) {
            where.and(policy.incomeCriteria.isNull().or(policy.incomeCriteria.eq(incomeCriteria)));
        }

        List<Policy> content = queryFactory
                .selectFrom(policy)
                .where(where)
                .orderBy(policy.endDate.asc().nullsLast())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(policy.count())
                .from(policy)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
}
