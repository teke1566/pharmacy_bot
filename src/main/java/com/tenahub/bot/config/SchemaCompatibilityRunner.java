package com.tenahub.bot.config;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaCompatibilityRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        List<String> ddlStatements = List.of(
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS inventory_held BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS pending_expires_at TIMESTAMP",
            "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS qr_token VARCHAR(255)",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS prescription_required BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS prescription_review_status VARCHAR(64) NOT NULL DEFAULT 'NOT_REQUIRED'",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS prescription_reviewed_at TIMESTAMP",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS prescription_reviewed_by BIGINT",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS prescription_rejection_reason VARCHAR(2000)",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS requires_prescription BOOLEAN NOT NULL DEFAULT FALSE",
                "CREATE TABLE IF NOT EXISTS reservation_prescription_files (id BIGSERIAL PRIMARY KEY, reservation_id BIGINT, reservation_group_id VARCHAR(255), user_id BIGINT NOT NULL, pharmacy_id BIGINT NOT NULL, medicine_id BIGINT, original_filename VARCHAR(1024) NOT NULL, content_type VARCHAR(255), file_size BIGINT, file_data BLOB NOT NULL, uploaded_at TIMESTAMP NOT NULL, review_status VARCHAR(64) NOT NULL, reviewed_at TIMESTAMP, reviewed_by BIGINT, rejection_reason VARCHAR(2000))",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS radius_km DOUBLE PRECISION NOT NULL DEFAULT 25",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS notification_cooldown_minutes INTEGER NOT NULL DEFAULT 180",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS max_notifications INTEGER NOT NULL DEFAULT 5",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS notifications_sent INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS last_notified_pharmacy_id BIGINT"
        );

        List<String> backfillStatements = List.of(
            "UPDATE medicine_reservations SET prescription_review_status = 'NOT_REQUIRED' WHERE prescription_review_status IS NULL",
                "UPDATE medicine_availability_alerts SET radius_km = 25 WHERE radius_km IS NULL",
                "UPDATE medicine_availability_alerts SET notification_cooldown_minutes = 180 WHERE notification_cooldown_minutes IS NULL",
                "UPDATE medicine_availability_alerts SET max_notifications = 5 WHERE max_notifications IS NULL",
                "UPDATE medicine_availability_alerts SET notifications_sent = 0 WHERE notifications_sent IS NULL"
        );

        ddlStatements.forEach(this::runSqlSafely);
        backfillStatements.forEach(this::runSqlSafely);
    }

    private void runSqlSafely(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Schema compatibility SQL failed: {} | reason={}", sql, e.getMessage());
        }
    }
}
