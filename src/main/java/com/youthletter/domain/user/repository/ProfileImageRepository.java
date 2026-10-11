package com.youthletter.domain.user.repository;

import com.youthletter.domain.user.entity.ProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, Long> {

    /** 프로필 응답용. 이미지 바이트는 읽지 않는다. */
    @Query("SELECT p.imageKey FROM ProfileImage p WHERE p.userId = :userId")
    Optional<UUID> findKeyByUserId(@Param("userId") Long userId);

    Optional<ProfileImage> findByImageKey(UUID imageKey);
}
