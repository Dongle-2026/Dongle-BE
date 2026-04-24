package com.youthletter.domain.spending.entity;

import com.youthletter.domain.user.entity.User;
import com.youthletter.global.util.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "spending_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpendingEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 이벤트 타입: CREATED, UPDATED, DELETED
     * Event Sourcing에서 이 테이블은 INSERT ONLY.
     * 수정/삭제도 새 이벤트를 추가하는 방식.
     */
    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    /**
     * 논리적 소비 레코드 ID.
     * 같은 spending_id의 이벤트를 시간순으로 재생하면 현재 상태를 복원 가능.
     */
    @Column(name = "spending_id", nullable = false)
    private Long spendingId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(length = 200)
    private String description;

    @Column(name = "spent_at", nullable = false)
    private LocalDate spentAt;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Builder
    public SpendingEvent(User user, String eventType, Long spendingId,
                         BigDecimal amount, String category, String description,
                         LocalDate spentAt, String metadata) {
        this.user = user;
        this.eventType = eventType;
        this.spendingId = spendingId;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.spentAt = spentAt;
        this.metadata = metadata;
    }
}
