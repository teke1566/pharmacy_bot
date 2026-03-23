package com.tenahub.bot.repository;

import com.tenahub.bot.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLocationRepository extends JpaRepository<UserLocation,Long> {

    Optional<UserLocation> findByTelegramId(Long telegramId);
}