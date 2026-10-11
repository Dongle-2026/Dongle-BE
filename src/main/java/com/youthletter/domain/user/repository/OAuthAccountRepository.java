package com.youthletter.domain.user.repository;

import com.youthletter.domain.user.entity.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByProviderAndProviderId(String provider, String providerId);

    @org.springframework.data.jpa.repository.Query("SELECT a.provider FROM OAuthAccount a WHERE a.user.id = :userId ORDER BY a.createdAt ASC")
    java.util.List<String> findProvidersByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}