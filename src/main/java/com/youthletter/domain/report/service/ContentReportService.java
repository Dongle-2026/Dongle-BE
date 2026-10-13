package com.youthletter.domain.report.service;

import com.youthletter.domain.report.dto.*;
import com.youthletter.domain.report.dto.AdminContentReportResponse.ReportStatus;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ContentReportService {
    private final JdbcTemplate jdbc;
    private static final RowMapper<AdminContentReportResponse> MAPPER = (rs, row) -> new AdminContentReportResponse(
            rs.getLong("id"), rs.getLong("user_id"), rs.getObject("quiz_id", Long.class), rs.getObject("card_news_id", Long.class),
            rs.getString("reason"), rs.getString("description"), ReportStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    @Transactional
    public ContentReportResponse reportQuiz(Long userId, Long quizId, ContentReportRequest request) {
        return report(userId, quizId, true, request);
    }

    @Transactional
    public ContentReportResponse reportCard(Long userId, Long cardId, ContentReportRequest request) {
        return report(userId, cardId, false, request);
    }

    private ContentReportResponse report(Long userId, Long targetId, boolean quiz, ContentReportRequest request) {
        String table = quiz ? "quizzes" : "card_news";
        String column = quiz ? "quiz_id" : "card_news_id";
        // 테이블/컬럼은 서버 내부 분기만 사용한다. 대상 삭제와 신고 저장의 경합을 막는다.
        if (jdbc.queryForList("SELECT id FROM " + table + " WHERE id=? FOR KEY SHARE", Long.class, targetId).isEmpty())
            throw new BusinessException(quiz ? ErrorCode.QUIZ_NOT_FOUND : ErrorCode.CARD_NEWS_NOT_FOUND);
        jdbc.update("INSERT INTO content_reports(user_id," + column + ",reason,description) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                userId, targetId, request.reason().name(), request.description() == null ? null : request.description().strip());
        // 재요청은 최초 신고를 그대로 반환한다. 처리 완료된 신고도 다시 OPEN으로 바꾸지 않는다.
        return jdbc.queryForObject("SELECT id,status,created_at FROM content_reports WHERE user_id=? AND " + column + "=?",
                (rs, row) -> new ContentReportResponse(rs.getLong("id"), rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class)),
                userId, targetId);
    }

    @Transactional(readOnly = true)
    public Page<AdminContentReportResponse> list(ReportStatus status, Pageable pageable) {
        String where = status == null ? "" : " WHERE status=?";
        Object[] countArgs = status == null ? new Object[]{} : new Object[]{status.name()};
        Object[] listArgs = status == null ? new Object[]{pageable.getPageSize(), pageable.getOffset()}
                : new Object[]{status.name(), pageable.getPageSize(), pageable.getOffset()};
        var rows = jdbc.query("SELECT * FROM content_reports" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", MAPPER, listArgs);
        Long total = jdbc.queryForObject("SELECT count(*) FROM content_reports" + where, Long.class, countArgs);
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Page<MyContentReportResponse> listMine(Long userId, ReportStatus status, Pageable pageable) {
        String where = " WHERE user_id=?" + (status == null ? "" : " AND status=?");
        Object[] countArgs = status == null ? new Object[]{userId} : new Object[]{userId, status.name()};
        Object[] listArgs = status == null ? new Object[]{userId, pageable.getPageSize(), pageable.getOffset()}
                : new Object[]{userId, status.name(), pageable.getPageSize(), pageable.getOffset()};
        var rows = jdbc.query("SELECT * FROM content_reports" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new MyContentReportResponse(rs.getLong("id"), rs.getObject("quiz_id", Long.class),
                        rs.getObject("card_news_id", Long.class), rs.getString("reason"), rs.getString("description"),
                        rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)), listArgs);
        Long total = jdbc.queryForObject("SELECT count(*) FROM content_reports" + where, Long.class, countArgs);
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Transactional
    public AdminContentReportResponse updateStatus(Long id, ReportStatus status) {
        return jdbc.query("UPDATE content_reports SET status=?,updated_at=now() WHERE id=? RETURNING *", MAPPER, status.name(), id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_REPORT_NOT_FOUND));
    }
}
