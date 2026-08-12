package com.tenahub.bot.repository;

import com.tenahub.bot.entity.BotTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BotTranslationRepository extends JpaRepository<BotTranslation, Long> {

    List<BotTranslation> findByLanguageCode(String languageCode);

    Optional<BotTranslation> findByLanguageCodeAndTranslationKey(String languageCode, String translationKey);
}
