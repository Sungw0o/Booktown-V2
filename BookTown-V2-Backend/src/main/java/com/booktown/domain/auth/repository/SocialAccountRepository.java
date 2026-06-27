package com.booktown.domain.auth.repository;

import com.booktown.domain.auth.entity.AuthProvider;
import com.booktown.domain.auth.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
