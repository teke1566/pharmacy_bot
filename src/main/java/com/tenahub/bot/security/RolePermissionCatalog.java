package com.tenahub.bot.security;

import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.entity.PharmacyStaffRole;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Code-seeded default role → permission matrix. Custom roles can reuse overrides later.
 */
public final class RolePermissionCatalog {

    private static final Map<PharmacyStaffRole, Set<PharmacyPermission>> DEFAULTS;

    static {
        Map<PharmacyStaffRole, Set<PharmacyPermission>> map = new EnumMap<>(PharmacyStaffRole.class);
        map.put(PharmacyStaffRole.PHARMACY_OWNER, EnumSet.allOf(PharmacyPermission.class));
        map.put(PharmacyStaffRole.PHARMACY_ADMIN, EnumSet.allOf(PharmacyPermission.class));

        EnumSet<PharmacyPermission> pharmacist = EnumSet.of(
                PharmacyPermission.INVENTORY_VIEW,
                PharmacyPermission.INVENTORY_ADJUST,
                PharmacyPermission.PRESCRIPTION_VIEW,
                PharmacyPermission.PRESCRIPTION_APPROVE,
                PharmacyPermission.PRESCRIPTION_REJECT,
                PharmacyPermission.RESERVATION_VIEW,
                PharmacyPermission.RESERVATION_APPROVE,
                PharmacyPermission.RESERVATION_FULFILL,
                PharmacyPermission.RESERVATION_CANCEL,
                PharmacyPermission.PRICE_VIEW,
                PharmacyPermission.SALES_VIEW,
                PharmacyPermission.SALES_CREATE,
                PharmacyPermission.SUPPLIER_VIEW,
                PharmacyPermission.PURCHASE_ORDER_VIEW,
                PharmacyPermission.REPORT_VIEW
        );
        map.put(PharmacyStaffRole.PHARMACIST, pharmacist);

        EnumSet<PharmacyPermission> inventoryManager = EnumSet.of(
                PharmacyPermission.INVENTORY_VIEW,
                PharmacyPermission.INVENTORY_CREATE,
                PharmacyPermission.INVENTORY_EDIT,
                PharmacyPermission.INVENTORY_ADJUST,
                PharmacyPermission.INVENTORY_ARCHIVE,
                PharmacyPermission.PRICE_VIEW,
                PharmacyPermission.SUPPLIER_VIEW,
                PharmacyPermission.SUPPLIER_CREATE,
                PharmacyPermission.SUPPLIER_EDIT,
                PharmacyPermission.PURCHASE_ORDER_VIEW,
                PharmacyPermission.PURCHASE_ORDER_CREATE,
                PharmacyPermission.PURCHASE_ORDER_APPROVE,
                PharmacyPermission.PURCHASE_ORDER_RECEIVE,
                PharmacyPermission.REPORT_VIEW,
                PharmacyPermission.RESERVATION_VIEW,
                PharmacyPermission.SALES_VIEW
        );
        map.put(PharmacyStaffRole.INVENTORY_MANAGER, inventoryManager);

        EnumSet<PharmacyPermission> cashier = EnumSet.of(
                PharmacyPermission.INVENTORY_VIEW,
                PharmacyPermission.PRICE_VIEW,
                PharmacyPermission.PRICE_DISCOUNT,
                PharmacyPermission.SALES_VIEW,
                PharmacyPermission.SALES_CREATE,
                PharmacyPermission.RESERVATION_VIEW,
                PharmacyPermission.RESERVATION_FULFILL
        );
        map.put(PharmacyStaffRole.CASHIER, cashier);

        EnumSet<PharmacyPermission> staff = EnumSet.of(
                PharmacyPermission.INVENTORY_VIEW,
                PharmacyPermission.PRESCRIPTION_VIEW,
                PharmacyPermission.RESERVATION_VIEW,
                PharmacyPermission.PRICE_VIEW,
                PharmacyPermission.SALES_VIEW,
                PharmacyPermission.SUPPLIER_VIEW,
                PharmacyPermission.PURCHASE_ORDER_VIEW,
                PharmacyPermission.REPORT_VIEW
        );
        map.put(PharmacyStaffRole.PHARMACY_STAFF, staff);

        DEFAULTS = Collections.unmodifiableMap(map);
    }

    private RolePermissionCatalog() {
    }

    public static Set<PharmacyPermission> defaultsFor(PharmacyStaffRole role) {
        if (role == null) {
            return EnumSet.noneOf(PharmacyPermission.class);
        }
        Set<PharmacyPermission> set = DEFAULTS.get(role);
        return set == null ? EnumSet.noneOf(PharmacyPermission.class) : EnumSet.copyOf(set);
    }

    public static Map<PharmacyStaffRole, Set<PharmacyPermission>> allDefaults() {
        return DEFAULTS;
    }
}
