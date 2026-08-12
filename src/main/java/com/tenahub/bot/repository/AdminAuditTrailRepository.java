package com.tenahub.bot.repository;

import com.tenahub.bot.entity.AdminAuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditTrailRepository extends JpaRepository<AdminAuditTrail, Long> {

    List<AdminAuditTrail> findTop30ByOrderByActionTimestampDesc();

    List<AdminAuditTrail> findTop30ByActionTypeOrderByActionTimestampDesc(String actionType);

    List<AdminAuditTrail> findTop30ByTargetEntityTypeOrderByActionTimestampDesc(String targetEntityType);
}
