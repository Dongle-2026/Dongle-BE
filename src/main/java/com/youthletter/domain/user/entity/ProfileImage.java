package com.youthletter.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** 사용자당 1장의 프로필 사진. 바이트는 DB(bytea)에 저장한다. */
@Entity
@Table(name = "profile_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImage {
    @Id @Column(name = "user_id") private Long userId;
    @Column(name = "image_key", nullable = false, unique = true) private UUID imageKey;
    @Column(nullable = false, columnDefinition = "bytea") private byte[] content;
    @Column(name = "content_type", nullable = false, length = 20) private String contentType;
    @Column(name = "byte_size", nullable = false) private int byteSize;
    private Integer width;
    private Integer height;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public ProfileImage(Long userId) { this.userId = userId; }

    public void replace(byte[] content, String contentType, Integer width, Integer height) {
        this.imageKey = UUID.randomUUID();
        this.content = content;
        this.contentType = contentType;
        this.byteSize = content.length;
        this.width = width;
        this.height = height;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
