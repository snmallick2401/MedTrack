package com.medtrack.inventory.service;

import com.medtrack.auth.entity.Role;
import com.medtrack.user.entity.User;
import com.medtrack.warehouse.entity.Warehouse;
import com.medtrack.warehouse.entity.WarehouseStatus;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class InventoryAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryAuthorizationService.class);

    /**
     * Determines whether the user holds a role with global inventory read visibility.
     * SUPER_ADMIN and AUDITOR operate enterprise-wide across all facilities.
     */
    public boolean isGlobalVisibilityRole(User actor) {
        if (actor == null || actor.getRole() == null) {
            return false;
        }
        String role = actor.getRole().getName();
        return "SUPER_ADMIN".equals(role) || "AUDITOR".equals(role);
    }

    /**
     * Resolves the warehouse ID that scopes the user's inventory queries.
     * Returns empty if the user is unassigned, inactive, or has an unauthorized role.
     */
    public Optional<UUID> getAuthorizedWarehouseScope(User actor) {
        if (actor == null || actor.getRole() == null) {
            return Optional.empty();
        }
        String role = actor.getRole().getName();
        if ("STORE_MANAGER".equals(role) || "CENTRAL_WAREHOUSE_MANAGER".equals(role)) {
            Warehouse assigned = actor.getAssignedWarehouse();
            if (assigned != null && assigned.getStatus() == WarehouseStatus.ACTIVE) {
                return Optional.of(assigned.getId());
            }
        }
        return Optional.empty();
    }

    /**
     * Safely resolves the authorized warehouse ID for inventory balance queries.
     *
     * Invariant:
     * - SUPER_ADMIN and AUDITOR may query any requested warehouse ID, or pass null for global scope.
     * - STORE_MANAGER and CENTRAL_WAREHOUSE_MANAGER are strictly confined to their active assigned warehouse.
     *   If requestedWarehouseId is provided, it must match assignedWarehouse.
     *   If requestedWarehouseId is null, it safely defaults to assignedWarehouse.
     * - Mismatched, missing, or inactive warehouse assignments fail closed with AccessDeniedException.
     * - All other roles fail closed with AccessDeniedException.
     *
     * @param actor The authenticated user querying inventory balances
     * @param requestedWarehouseId The warehouse ID requested by the client (may be null)
     * @return The authorized warehouse ID to query (or null for global unconstrained queries)
     * @throws AccessDeniedException if authorization fails
     */
    public UUID resolveAuthorizedWarehouse(User actor, UUID requestedWarehouseId) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=VIEW_INVENTORY");
            throw new AccessDeniedException("Authenticated actor is required");
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();

        // 1. Global roles (SUPER_ADMIN, AUDITOR)
        if (isGlobalVisibilityRole(actor)) {
            return requestedWarehouseId;
        }

        // 2. Facility-scoped roles (STORE_MANAGER, CENTRAL_WAREHOUSE_MANAGER)
        if ("STORE_MANAGER".equals(roleName) || "CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assigned = actor.getAssignedWarehouse();
            if (assigned == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, requested_warehouse={}, resource_type=InventoryBalance, requested_action=VIEW_INVENTORY, reason=No assigned warehouse",
                        actorId, roleName, requestedWarehouseId);
                throw new AccessDeniedException("User has no assigned warehouse");
            }

            if (assigned.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, requested_warehouse={}, resource_type=InventoryBalance, requested_action=VIEW_INVENTORY, reason=Assigned warehouse is inactive",
                        actorId, roleName, assigned.getId(), requestedWarehouseId);
                throw new AccessDeniedException("User assigned warehouse is inactive");
            }

            if (requestedWarehouseId != null && !assigned.getId().equals(requestedWarehouseId)) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, requested_warehouse={}, resource_type=InventoryBalance, requested_action=VIEW_INVENTORY, reason=Facility mismatch",
                        actorId, roleName, assigned.getId(), requestedWarehouseId);
                throw new AccessDeniedException("User is not authorized to view inventory balances for warehouse: " + requestedWarehouseId);
            }

            return assigned.getId();
        }

        // 3. Fail-closed for all other roles (e.g., LOGISTICS_COORDINATOR or unknown)
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, requested_warehouse={}, resource_type=InventoryBalance, requested_action=VIEW_INVENTORY, reason=Unauthorized role for viewing inventory balances",
                actorId, roleName, requestedWarehouseId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to view inventory balances");
    }

    /**
     * Enforces that the actor is authorized to view balances for the specific requested warehouse ID.
     */
    public void assertCanViewWarehouseBalances(User actor, UUID requestedWarehouseId) {
        if (requestedWarehouseId == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing target warehouse ID, requested_action=VIEW_INVENTORY");
            throw new AccessDeniedException("Warehouse ID is required");
        }
        resolveAuthorizedWarehouse(actor, requestedWarehouseId);
    }

    /**
     * Safely resolves the authorized warehouse ID for report generation and export.
     *
     * Invariant:
     * - SUPER_ADMIN and AUDITOR may export reports for any warehouse ID, or pass null for global report.
     * - STORE_MANAGER and CENTRAL_WAREHOUSE_MANAGER are strictly confined to their active assigned warehouse.
     *   If requestedWarehouseId is provided, it must match assignedWarehouse.
     *   If requestedWarehouseId is null, it safely defaults to assignedWarehouse.
     * - Mismatched, missing, or inactive warehouse assignments fail closed with AccessDeniedException.
     * - All other roles fail closed with AccessDeniedException.
     *
     * Emits structured SOC log:
     * SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, requested_action=EXPORT_REPORT, report_type=...
     */
    public UUID resolveAuthorizedWarehouseForReport(User actor, UUID requestedWarehouseId, String reportType) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=EXPORT_REPORT, report_type={}", reportType);
            throw new AccessDeniedException("Authenticated actor is required");
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();

        // 1. Global roles (SUPER_ADMIN, AUDITOR)
        if (isGlobalVisibilityRole(actor)) {
            return requestedWarehouseId;
        }

        // 2. Facility-scoped roles (STORE_MANAGER, CENTRAL_WAREHOUSE_MANAGER)
        if ("STORE_MANAGER".equals(roleName) || "CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assigned = actor.getAssignedWarehouse();
            if (assigned == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, requested_warehouse={}, resource_type=Report, requested_action=EXPORT_REPORT, report_type={}, reason=No assigned warehouse",
                        actorId, roleName, requestedWarehouseId, reportType);
                throw new AccessDeniedException("User has no assigned warehouse");
            }

            if (assigned.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, requested_warehouse={}, resource_type=Report, requested_action=EXPORT_REPORT, report_type={}, reason=Assigned warehouse is inactive",
                        actorId, roleName, assigned.getId(), requestedWarehouseId, reportType);
                throw new AccessDeniedException("User assigned warehouse is inactive");
            }

            if (requestedWarehouseId != null && !assigned.getId().equals(requestedWarehouseId)) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, requested_warehouse={}, resource_type=Report, requested_action=EXPORT_REPORT, report_type={}, reason=Facility mismatch",
                        actorId, roleName, assigned.getId(), requestedWarehouseId, reportType);
                throw new AccessDeniedException("User is not authorized to export reports for warehouse: " + requestedWarehouseId);
            }

            return assigned.getId();
        }

        // 3. Fail-closed for all other roles
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, requested_warehouse={}, resource_type=Report, requested_action=EXPORT_REPORT, report_type={}, reason=Unauthorized role for report export",
                actorId, roleName, requestedWarehouseId, reportType);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to generate or export reports");
    }
}
