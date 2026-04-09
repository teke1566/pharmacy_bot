package com.tenahub.bot.repository;

import com.tenahub.bot.entity.MiniAppPhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MiniAppPhoneVerificationRepository extends JpaRepository<MiniAppPhoneVerification, Long> {

    Optional<MiniAppPhoneVerification> findTopByPhoneAndCodeAndCodeUsedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(
            String phone,
            String code,
            LocalDateTime time
    );

    Optional<MiniAppPhoneVerification> findTopByPhoneAndVerificationTokenAndVerificationTokenUsedFalseAndVerificationTokenExpiresAtAfterOrderByVerifiedAtDesc(
            String phone,
            String verificationToken,
            LocalDateTime time
    );
}