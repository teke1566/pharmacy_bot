package com.tenahub.bot.config;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
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
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS hidden_from_user_at TIMESTAMP",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS hidden_from_pharmacy_at TIMESTAMP",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS requires_prescription BOOLEAN NOT NULL DEFAULT FALSE",
                "CREATE TABLE IF NOT EXISTS medicines (id BIGSERIAL PRIMARY KEY, name VARCHAR(255) NOT NULL, canonical_name VARCHAR(255) NOT NULL UNIQUE, active_ingredient VARCHAR(255), strength VARCHAR(255), dosage_form VARCHAR(255), manufacturer VARCHAR(255), category VARCHAR(255), image_url VARCHAR(1024), prescription_required BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP, updated_at TIMESTAMP)",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS catalog_medicine_id BIGINT",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS batch_number VARCHAR(64)",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS expiry_date DATE",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS strength VARCHAR(64)",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS dosage_form VARCHAR(64)",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE",
                "CREATE TABLE IF NOT EXISTS reservation_prescription_files (id BIGSERIAL PRIMARY KEY, reservation_id BIGINT, reservation_group_id VARCHAR(255), user_id BIGINT NOT NULL, pharmacy_id BIGINT NOT NULL, medicine_id BIGINT, original_filename VARCHAR(1024) NOT NULL, content_type VARCHAR(255), file_size BIGINT, file_data BLOB NOT NULL, uploaded_at TIMESTAMP NOT NULL, review_status VARCHAR(64) NOT NULL, reviewed_at TIMESTAMP, reviewed_by BIGINT, rejection_reason VARCHAR(2000))",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS radius_km DOUBLE PRECISION NOT NULL DEFAULT 25",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS notification_cooldown_minutes INTEGER NOT NULL DEFAULT 180",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS max_notifications INTEGER NOT NULL DEFAULT 5",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS notifications_sent INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP",
                "ALTER TABLE medicine_availability_alerts ADD COLUMN IF NOT EXISTS last_notified_pharmacy_id BIGINT",
                "CREATE TABLE IF NOT EXISTS medicine_batches (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, inventory_id BIGINT NOT NULL, medicine_name VARCHAR(255), batch_number VARCHAR(64), expiry_date DATE, quantity INTEGER NOT NULL DEFAULT 0, purchase_price NUMERIC(12,2), selling_price NUMERIC(12,2), supplier VARCHAR(255), received_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_medicine_batches_inventory ON medicine_batches(inventory_id)",
                "CREATE INDEX IF NOT EXISTS idx_medicine_batches_pharmacy_expiry ON medicine_batches(pharmacy_id, expiry_date)",
                "CREATE TABLE IF NOT EXISTS stock_movements (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, inventory_id BIGINT NOT NULL, batch_id BIGINT, medicine_name VARCHAR(255), movement_type VARCHAR(32) NOT NULL, quantity_change INTEGER NOT NULL, quantity_before INTEGER, quantity_after INTEGER, batch_quantity_before INTEGER, batch_quantity_after INTEGER, actor_telegram_id BIGINT, reason VARCHAR(2000), reservation_id BIGINT, created_at TIMESTAMP NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_stock_movements_inventory ON stock_movements(inventory_id)",
                "CREATE INDEX IF NOT EXISTS idx_stock_movements_reservation ON stock_movements(reservation_id)",
                "CREATE INDEX IF NOT EXISTS idx_stock_movements_pharmacy_created ON stock_movements(pharmacy_id, created_at)",
                "ALTER TABLE medicine_batches ADD COLUMN IF NOT EXISTS supplier_id BIGINT",
                "CREATE TABLE IF NOT EXISTS pharmacy_suppliers (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, name VARCHAR(255) NOT NULL, phone VARCHAR(64), email VARCHAR(255), address VARCHAR(1000), contact_person VARCHAR(255), status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', notes VARCHAR(2000), created_at TIMESTAMP, updated_at TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_suppliers_pharmacy ON pharmacy_suppliers(pharmacy_id)",
                "CREATE TABLE IF NOT EXISTS purchase_orders (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, supplier_id BIGINT, status VARCHAR(32) NOT NULL, notes VARCHAR(2000), actor_telegram_id BIGINT, created_at TIMESTAMP, ordered_at TIMESTAMP, received_at TIMESTAMP, cancelled_at TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_purchase_orders_pharmacy ON purchase_orders(pharmacy_id, created_at)",
                "CREATE TABLE IF NOT EXISTS purchase_order_items (id BIGSERIAL PRIMARY KEY, purchase_order_id BIGINT NOT NULL, medicine_name VARCHAR(255) NOT NULL, quantity_ordered INTEGER NOT NULL, quantity_received INTEGER NOT NULL DEFAULT 0, purchase_price NUMERIC(12,2), selling_price NUMERIC(12,2), batch_number VARCHAR(64), expiry_date DATE)",
                "CREATE INDEX IF NOT EXISTS idx_purchase_order_items_order ON purchase_order_items(purchase_order_id)",
                "CREATE TABLE IF NOT EXISTS pharmacy_sales (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, reservation_id BIGINT, customer_name VARCHAR(255), actor_telegram_id BIGINT, total_amount NUMERIC(12,2), currency VARCHAR(8) DEFAULT 'ETB', created_at TIMESTAMP NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_pharmacy_sales_reservation ON pharmacy_sales(reservation_id) WHERE reservation_id IS NOT NULL",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_sales_pharmacy_created ON pharmacy_sales(pharmacy_id, created_at)",
                "CREATE TABLE IF NOT EXISTS pharmacy_sale_items (id BIGSERIAL PRIMARY KEY, sale_id BIGINT NOT NULL, pharmacy_id BIGINT NOT NULL, medicine_name VARCHAR(255) NOT NULL, quantity INTEGER NOT NULL, unit_price NUMERIC(12,2), total_price NUMERIC(12,2), batch_id BIGINT)",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_sale_items_sale ON pharmacy_sale_items(sale_id)",
                "CREATE TABLE IF NOT EXISTS restock_ignores (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, medicine_name VARCHAR(255) NOT NULL, ignored_at TIMESTAMP NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_restock_ignores_pharmacy_med ON restock_ignores(pharmacy_id, lower(medicine_name))",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS prescription_clarification_message VARCHAR(2000)",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS prescription_clarification_at TIMESTAMP",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS fulfilled_by_telegram_id BIGINT",
                "CREATE TABLE IF NOT EXISTS pharmacy_notifications (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, type VARCHAR(64) NOT NULL, title VARCHAR(255) NOT NULL, message VARCHAR(4000) NOT NULL, reservation_id BIGINT, medicine_name VARCHAR(255), read_at TIMESTAMP, created_at TIMESTAMP NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_notifications_pharmacy_created ON pharmacy_notifications(pharmacy_id, created_at)",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_notifications_pharmacy_unread ON pharmacy_notifications(pharmacy_id, read_at)",
                "CREATE TABLE IF NOT EXISTS reservation_status_history (id BIGSERIAL PRIMARY KEY, reservation_id BIGINT NOT NULL, pharmacy_id BIGINT NOT NULL, from_status VARCHAR(64), to_status VARCHAR(64), actor_telegram_id BIGINT, reason VARCHAR(2000), created_at TIMESTAMP NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_reservation_status_history_reservation ON reservation_status_history(reservation_id, created_at)",
                "CREATE TABLE IF NOT EXISTS pharmacy_staff (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, telegram_id BIGINT, employee_id VARCHAR(64) NOT NULL, first_name VARCHAR(128), last_name VARCHAR(128), email VARCHAR(255), phone VARCHAR(64), role VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, license_info VARCHAR(512), notes VARCHAR(2000), photo_url VARCHAR(1024), start_date DATE, invited_at TIMESTAMP, joined_at TIMESTAMP, last_active_at TIMESTAMP, last_login_at TIMESTAMP, suspended_at TIMESTAMP, suspend_reason VARCHAR(2000), disabled_at TIMESTAMP, version BIGINT DEFAULT 0, created_at TIMESTAMP, updated_at TIMESTAMP)",
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_pharmacy_staff_employee ON pharmacy_staff(pharmacy_id, employee_id)",
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_pharmacy_staff_telegram ON pharmacy_staff(pharmacy_id, telegram_id) WHERE telegram_id IS NOT NULL",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_staff_pharmacy ON pharmacy_staff(pharmacy_id, status)",
                "CREATE TABLE IF NOT EXISTS pharmacy_staff_invites (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, staff_id BIGINT NOT NULL, token_hash VARCHAR(128) NOT NULL UNIQUE, invited_telegram_id BIGINT, expires_at TIMESTAMP NOT NULL, accepted_at TIMESTAMP, revoked_at TIMESTAMP, created_at TIMESTAMP NOT NULL, created_by_telegram_id BIGINT)",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_staff_invites_staff ON pharmacy_staff_invites(staff_id)",
                "CREATE TABLE IF NOT EXISTS pharmacy_staff_permission_overrides (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, staff_id BIGINT NOT NULL, permission VARCHAR(64) NOT NULL, effect VARCHAR(16) NOT NULL, created_at TIMESTAMP NOT NULL, created_by_telegram_id BIGINT)",
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_staff_permission ON pharmacy_staff_permission_overrides(staff_id, permission)",
                "CREATE TABLE IF NOT EXISTS pharmacy_audit_events (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, actor_telegram_id BIGINT, staff_id BIGINT, employee_id VARCHAR(64), user_name_snapshot VARCHAR(255), action VARCHAR(64) NOT NULL, module VARCHAR(64) NOT NULL, entity_type VARCHAR(64), entity_id VARCHAR(128), old_value VARCHAR(4000), new_value VARCHAR(4000), reason VARCHAR(2000), correlation_id VARCHAR(128), created_at TIMESTAMP NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_audit_pharmacy_created ON pharmacy_audit_events(pharmacy_id, created_at)",
                "CREATE INDEX IF NOT EXISTS idx_pharmacy_audit_staff ON pharmacy_audit_events(pharmacy_id, staff_id)",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0",
                "ALTER TABLE pharmacy_inventory ADD COLUMN IF NOT EXISTS purchase_cost NUMERIC(12,2)",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS unit_price NUMERIC(12,2)",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS total_price NUMERIC(12,2)",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS currency VARCHAR(8)",
                "ALTER TABLE medicine_reservations ADD COLUMN IF NOT EXISTS price_locked_at TIMESTAMP",
                "CREATE TABLE IF NOT EXISTS pharmacy_pricing_policies (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL UNIQUE, approval_threshold_percent NUMERIC(8,2), timezone VARCHAR(64) NOT NULL DEFAULT 'Africa/Addis_Ababa', tax_rate NUMERIC(8,4) NOT NULL DEFAULT 0, costing_method VARCHAR(32) NOT NULL DEFAULT 'WEIGHTED_AVERAGE', currency VARCHAR(8) DEFAULT 'ETB', updated_at TIMESTAMP)",
                "CREATE TABLE IF NOT EXISTS price_history (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, inventory_id BIGINT NOT NULL, medicine_name VARCHAR(255), old_selling_price NUMERIC(12,2), new_selling_price NUMERIC(12,2), old_purchase_cost NUMERIC(12,2), new_purchase_cost NUMERIC(12,2), currency VARCHAR(8), reason VARCHAR(2000), actor_staff_id BIGINT, actor_telegram_id BIGINT, actor_name_snapshot VARCHAR(255), request_id BIGINT, effective_at TIMESTAMP, created_at TIMESTAMP NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_price_history_inventory ON price_history(inventory_id, created_at)",
                "CREATE INDEX IF NOT EXISTS idx_price_history_pharmacy ON price_history(pharmacy_id, created_at)",
                "CREATE TABLE IF NOT EXISTS price_change_requests (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, inventory_id BIGINT NOT NULL, medicine_name VARCHAR(255), current_selling_price NUMERIC(12,2), proposed_selling_price NUMERIC(12,2), purchase_cost_ref NUMERIC(12,2), margin_before NUMERIC(8,4), margin_after NUMERIC(8,4), percent_change NUMERIC(8,4), currency VARCHAR(8), effective_at TIMESTAMP, reason VARCHAR(2000), status VARCHAR(32) NOT NULL, requested_by_staff_id BIGINT, requested_by_telegram_id BIGINT, requested_at TIMESTAMP, approved_by_staff_id BIGINT, approved_by_telegram_id BIGINT, approved_at TIMESTAMP, rejection_reason VARCHAR(2000), version BIGINT DEFAULT 0, created_at TIMESTAMP, updated_at TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_price_change_requests_pharmacy_status ON price_change_requests(pharmacy_id, status)",
                "CREATE INDEX IF NOT EXISTS idx_price_change_requests_effective ON price_change_requests(status, effective_at)",
                "CREATE TABLE IF NOT EXISTS promotions (id BIGSERIAL PRIMARY KEY, pharmacy_id BIGINT NOT NULL, inventory_id BIGINT NOT NULL, medicine_name VARCHAR(255), discount_type VARCHAR(32) NOT NULL, discount_value NUMERIC(12,2) NOT NULL, start_at TIMESTAMP NOT NULL, end_at TIMESTAMP NOT NULL, min_quantity INTEGER, max_discount NUMERIC(12,2), status VARCHAR(32) NOT NULL, created_by_staff_id BIGINT, created_by_telegram_id BIGINT, created_at TIMESTAMP, updated_at TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_promotions_pharmacy_status ON promotions(pharmacy_id, status)",
                "CREATE INDEX IF NOT EXISTS idx_promotions_inventory ON promotions(inventory_id, status)"
        );

        List<String> backfillStatements = List.of(
            "UPDATE medicine_reservations SET prescription_review_status = 'NOT_REQUIRED' WHERE prescription_review_status IS NULL",
                "UPDATE medicine_availability_alerts SET radius_km = 25 WHERE radius_km IS NULL",
                "UPDATE medicine_availability_alerts SET notification_cooldown_minutes = 180 WHERE notification_cooldown_minutes IS NULL",
                "UPDATE medicine_availability_alerts SET max_notifications = 5 WHERE max_notifications IS NULL",
                "UPDATE medicine_availability_alerts SET notifications_sent = 0 WHERE notifications_sent IS NULL",
                "INSERT INTO medicine_batches (pharmacy_id, inventory_id, medicine_name, batch_number, expiry_date, quantity, selling_price, received_at, created_at, updated_at) SELECT pi.pharmacy_id, pi.id, pi.medicine_name, pi.batch_number, pi.expiry_date, COALESCE(pi.quantity, 0), pi.price, COALESCE(pi.updated_at, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM pharmacy_inventory pi WHERE COALESCE(pi.quantity, 0) > 0 AND NOT EXISTS (SELECT 1 FROM medicine_batches mb WHERE mb.inventory_id = pi.id)"
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
