package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineSearchLog;
import com.tenahub.bot.repository.MedicineSearchLogRepository;
import com.tenahub.bot.service.MedicineSearchLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicineSearchLogServiceImpl implements MedicineSearchLogService {

    private final MedicineSearchLogRepository repository;

    @Override
    public void logSearch(Long userId, String medicineName) {
        if (userId == null || medicineName == null || medicineName.isBlank()) {
            return;
        }

        MedicineSearchLog log = new MedicineSearchLog();
        log.setUserId(userId);
        log.setMedicineName(medicineName.trim().toLowerCase());

        repository.save(log);
    }

    @Override
    public List<String> getRecentSearches(Long userId) {
        return repository.findTop10ByUserIdOrderBySearchedAtDesc(userId).stream()
                .map(MedicineSearchLog::getMedicineName)
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }
}