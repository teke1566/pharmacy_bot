package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PharmacyAuditEventRepository extends JpaRepository<PharmacyAuditEvent, Long> {

    List<PharmacyAuditEvent> findByPharmacyIdAndStaffIdOrderByCreatedAtDesc(Long pharmacyId, Long staffId);

    @Query("""
            SELECT e FROM PharmacyAuditEvent e
            WHERE e.pharmacyId = :pharmacyId
              AND (:staffId IS NULL OR e.staffId = :staffId)
              AND (:module IS NULL OR e.module = :module)
              AND (:action IS NULL OR e.action = :action)
              AND e.createdAt >= :from
              AND e.createdAt < :to
            ORDER BY e.createdAt DESC
            """)
    List<PharmacyAuditEvent> search(@Param("pharmacyId") Long pharmacyId,
                                    @Param("staffId") Long staffId,
                                    @Param("module") String module,
                                    @Param("action") String action,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    long countByPharmacyIdAndCreatedAtGreaterThanEqual(Long pharmacyId, LocalDateTime from);
}
