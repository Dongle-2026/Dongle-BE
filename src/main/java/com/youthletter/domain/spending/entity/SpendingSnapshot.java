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

@Entity
@Table(name = "spending_snapshots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "snapshot_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpendingSnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** '2026-03' 형식 */
    @Column(name = "snapshot_month", nullable = false, length = 7)
    private String snapshotMonth;

    /** {"FOOD": 350000, "TRANSPORT": 120000, ...} */
    @Type(JsonType.class)
    @Column(name = "category_totals", nullable = false, columnDefinition = "jsonb")
    private String categoryTotals;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Builder
    public SpendingSnapshot(User user, String snapshotMonth,
                            String categoryTotals, BigDecimal totalAmount) {
        this.user = user;
        this.snapshotMonth = snapshotMonth;
        this.categoryTotals = categoryTotals;
        this.totalAmount = totalAmount;
    }

    public void updateTotals(String categoryTotals, BigDecimal totalAmount) {
        this.categoryTotals = categoryTotals;
        this.totalAmount = totalAmount;
    }
}
