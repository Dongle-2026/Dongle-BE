package com.youthletter.domain.spending.entity;

import com.youthletter.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "spending_budgets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpendingBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "monthly_budget", nullable = false)
    private Long monthlyBudget;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public SpendingBudget(User user, Long monthlyBudget) {
        this.user = user;
        this.monthlyBudget = monthlyBudget;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(Long monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
        this.updatedAt = LocalDateTime.now();
    }
}
