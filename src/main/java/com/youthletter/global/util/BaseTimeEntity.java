package com.youthletter.global.util;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * created_at + updated_at이 모두 필요한 엔티티용.
 * users, financial_terms, policies 등 수정이 발생하는 테이블에 사용.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
