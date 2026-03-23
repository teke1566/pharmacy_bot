package com.tenahub.bot.repository;

import com.tenahub.bot.entity.MedicineSearchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MedicineSearchLogRepository extends JpaRepository<MedicineSearchLog, Long> {

List<MedicineSearchLog> findBySearchedAtBetween(LocalDateTime start, LocalDateTime end);

List<MedicineSearchLog> findByMedicineNameIgnoreCaseAndSearchedAtBetween(
        String medicineName,
        LocalDateTime start,
        LocalDateTime end
);
    List<MedicineSearchLog> findTop10ByUserIdOrderBySearchedAtDesc(Long userId);
}