package com.youthletter.domain.user.repository;

import com.youthletter.domain.user.entity.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByProviderAndProviderId(String provider, String providerId);

    /** 탈퇴(soft delete) 사용자 연결도 찾기 위해 사용자 엔티티를 로딩하지 않고 ID만 조회한다. */
    @org.springframework.data.jpa.repository.Query("SELECT a.user.id FROM OAuthAccount a WHERE a.provider = :provider AND a.providerId = :providerId")
    Optional<Long> findUserIdByProviderAndProviderId(@org.springframework.data.repository.query.Param("provider") String provider,
                                                     @org.springframework.data.repository.query.Param("providerId") String providerId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM OAuthAccount a WHERE a.provider = :provider AND a.providerId = :providerId")
    void deleteByProviderAndProviderId(@org.springframework.data.repository.query.Param("provider") String provider,
                                       @org.springframework.data.repository.query.Param("providerId") String providerId);

    @org.springframework.data.jpa.repository.Query("SELECT a.provider FROM OAuthAccount a WHERE a.user.id = :userId ORDER BY a.createdAt ASC")
    java.util.List<String> findProvidersByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}