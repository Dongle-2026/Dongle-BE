package com.youthletter.domain.user.repository;

import com.youthletter.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByNickname(String nickname);

    /**
     * 이메일/닉네임 UNIQUE 제약은 탈퇴(soft delete) 행에도 적용되므로, 중복 검사도 탈퇴 사용자를 포함한다.
     * (엔티티의 @SQLRestriction을 우회하려고 native query 사용. 제외하면 검사 통과 후 INSERT가 500으로 실패한다.)
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)", nativeQuery = true)
    boolean existsByEmail(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE nickname = :nickname)", nativeQuery = true)
    boolean existsByNickname(@org.springframework.data.repository.query.Param("nickname") String nickname);
}
