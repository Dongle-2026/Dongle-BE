package com.youthletter.domain.stock.entity;

import com.youthletter.domain.user.entity.User;
import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_watchlists",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ticker"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockWatchlist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Builder
    public StockWatchlist(User user, String ticker) {
        this.user = user;
        this.ticker = ticker;
    }
}
