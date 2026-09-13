package com.medtrack.transfer.service;

import com.medtrack.auth.entity.Role;
import com.medtrack.transfer.entity.StockTransfer;
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
public class TransferAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(TransferAuthorizationService.class);

    /**
     * Determines whether the user holds a role with global read visibility.
     * SUPER_ADMIN, AUDITOR, and LOGISTICS_COORDINATOR operate enterprise-wide.
     */
    public boolean isGlobalVisibilityRole(User actor) {
        if (actor == null || actor.getRole() == null) {
            return false;
        }
        String role = actor.getRole().getName();
        return "SUPER_ADMIN".equals(role) || "AUDITOR".equals(role) || "LOGISTICS_COORDINATOR".equals(role);
    }

    /**
     * Resolves the warehouse ID that scopes the user's queries.
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
     * Enforces facility-level authorization for viewing a specific stock transfer.
     *
     * Invariant:
     * - SUPER_ADMIN, AUDITOR, and LOGISTICS_COORDINATOR have global read visibility.
     * - STORE_MANAGER and CENTRAL_WAREHOUSE_MANAGER may view transfers where their active
     *   assigned warehouse is either the source or destination facility.
     * - Mismatches or missing/inactive facility associations fail closed.
     */
    public void assertCanViewTransfer(User actor, StockTransfer transfer) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=VIEW_TRANSFER");
            throw new AccessDeniedException("Authenticated actor is required");
        }
        if (transfer == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target transfer, requested_action=VIEW_TRANSFER", actor.getId());
            throw new AccessDeniedException("Stock transfer is required");
        }

        if (isGlobalVisibilityRole(actor)) {
            return;
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();
        UUID transferId = transfer.getId();

        if ("STORE_MANAGER".equals(roleName) || "CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assigned = actor.getAssignedWarehouse();
            if (assigned == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=VIEW_TRANSFER, reason=No assigned warehouse",
                        actorId, roleName, transferId);
                throw new AccessDeniedException("User has no assigned warehouse");
            }
            if (assigned.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=VIEW_TRANSFER, reason=Assigned warehouse is inactive",
                        actorId, roleName, assigned.getId(), transferId);
                throw new AccessDeniedException("User assigned warehouse is inactive");
            }

            boolean isSource = transfer.getSourceWarehouse() != null && assigned.getId().equals(transfer.getSourceWarehouse().getId());
            boolean isDestination = transfer.getDestinationWarehouse() != null && assigned.getId().equals(transfer.getDestinationWarehouse().getId());

            if (isSource || isDestination) {
                return;
            }

            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=VIEW_TRANSFER, reason=Facility mismatch",
                    actorId, roleName, assigned.getId(), transferId);
            throw new AccessDeniedException("User is not authorized to view transfer outside assigned facility");
        }

        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=VIEW_TRANSFER, reason=Unauthorized role for viewing transfer",
                actorId, roleName, transferId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to view transfers");
    }

    /**
     * Enforces facility-level and role-level authorization for allocating inventory to a stock transfer.
     *
     * Invariant:
     * - SUPER_ADMIN has global administrative authorization.
     * - CENTRAL_WAREHOUSE_MANAGER may only allocate transfers where transfer.sourceWarehouse
     *   matches their active assignedWarehouse.
     * - Missing/inactive warehouse assignments fail closed.
     * - Destination warehouse assignment does NOT grant allocation authority.
     * - All other roles (STORE_MANAGER, LOGISTICS_COORDINATOR, AUDITOR, etc.) are denied.
     *
     * @param actor The authenticated user executing the allocation
     * @param transfer The stock transfer being allocated
     * @throws AccessDeniedException if authorization fails
     */
    public void assertCanAllocateTransfer(User actor, StockTransfer transfer) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=ALLOCATE_TRANSFER");
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (transfer == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target transfer, requested_action=ALLOCATE_TRANSFER", actor.getId());
            throw new AccessDeniedException("Stock transfer is required");
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();
        UUID transferId = transfer.getId();

        // SUPER_ADMIN has global administrative authority
        if ("SUPER_ADMIN".equals(roleName)) {
            return;
        }

        if ("CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assignedWarehouse = actor.getAssignedWarehouse();
            if (assignedWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=ALLOCATE_TRANSFER, reason=No assigned warehouse",
                        actorId, transferId);
                throw new AccessDeniedException("Central warehouse manager has no assigned warehouse");
            }

            if (assignedWarehouse.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=ALLOCATE_TRANSFER, reason=Assigned warehouse is inactive",
                        actorId, assignedWarehouse.getId(), transferId);
                throw new AccessDeniedException("Central warehouse manager assigned warehouse is inactive");
            }

            Warehouse sourceWarehouse = transfer.getSourceWarehouse();
            if (sourceWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=ALLOCATE_TRANSFER, reason=Transfer source warehouse is missing",
                        actorId, transferId);
                throw new AccessDeniedException("Transfer source warehouse is missing");
            }

            if (!assignedWarehouse.getId().equals(sourceWarehouse.getId())) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, resource_warehouse={}, requested_action=ALLOCATE_TRANSFER, reason=Facility mismatch",
                        actorId, assignedWarehouse.getId(), transferId, sourceWarehouse.getId());
                throw new AccessDeniedException("Central warehouse manager is not assigned to source warehouse: " + sourceWarehouse.getId());
            }

            return;
        }

        // Fail-closed for all other roles (e.g. STORE_MANAGER, LOGISTICS_COORDINATOR, AUDITOR)
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=ALLOCATE_TRANSFER, reason=Unauthorized role for allocation",
                actorId, roleName, transferId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to allocate transfers");
    }

    /**
     * Enforces facility-level and role-level authorization for picking stock transfer items.
     *
     * Invariant:
     * - SUPER_ADMIN has global administrative authorization.
     * - CENTRAL_WAREHOUSE_MANAGER may only pick transfers where transfer.sourceWarehouse
     *   matches their active assignedWarehouse.
     * - Missing/inactive warehouse assignments fail closed.
     * - Destination warehouse assignment does NOT grant picking authority.
     * - All other roles (STORE_MANAGER, LOGISTICS_COORDINATOR, AUDITOR, etc.) are denied.
     *
     * @param actor The authenticated user executing the pick
     * @param transfer The stock transfer being picked
     * @throws AccessDeniedException if authorization fails
     */
    public void assertCanPickTransfer(User actor, StockTransfer transfer) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=PICK_TRANSFER");
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (transfer == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target transfer, requested_action=PICK_TRANSFER", actor.getId());
            throw new AccessDeniedException("Stock transfer is required");
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();
        UUID transferId = transfer.getId();

        // SUPER_ADMIN has global administrative authority
        if ("SUPER_ADMIN".equals(roleName)) {
            return;
        }

        if ("CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assignedWarehouse = actor.getAssignedWarehouse();
            if (assignedWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=PICK_TRANSFER, reason=No assigned warehouse",
                        actorId, transferId);
                throw new AccessDeniedException("Central warehouse manager has no assigned warehouse");
            }

            if (assignedWarehouse.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=PICK_TRANSFER, reason=Assigned warehouse is inactive",
                        actorId, assignedWarehouse.getId(), transferId);
                throw new AccessDeniedException("Central warehouse manager assigned warehouse is inactive");
            }

            Warehouse sourceWarehouse = transfer.getSourceWarehouse();
            if (sourceWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=PICK_TRANSFER, reason=Transfer source warehouse is missing",
                        actorId, transferId);
                throw new AccessDeniedException("Transfer source warehouse is missing");
            }

            if (!assignedWarehouse.getId().equals(sourceWarehouse.getId())) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, resource_warehouse={}, requested_action=PICK_TRANSFER, reason=Facility mismatch",
                        actorId, assignedWarehouse.getId(), transferId, sourceWarehouse.getId());
                throw new AccessDeniedException("Central warehouse manager is not assigned to source warehouse: " + sourceWarehouse.getId());
            }

            return;
        }

        // Fail-closed for all other roles (e.g. STORE_MANAGER, LOGISTICS_COORDINATOR, AUDITOR)
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=PICK_TRANSFER, reason=Unauthorized role for picking",
                actorId, roleName, transferId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to pick transfers");
    }

    /**
     * Enforces facility-level and role-level authorization for packing stock transfer items.
     *
     * Invariant:
     * - SUPER_ADMIN has global administrative authorization.
     * - CENTRAL_WAREHOUSE_MANAGER may only pack transfers where transfer.sourceWarehouse
     *   matches their active assignedWarehouse.
     * - Missing/inactive warehouse assignments fail closed.
     * - Destination warehouse assignment does NOT grant packing authority.
     * - All other roles (STORE_MANAGER, LOGISTICS_COORDINATOR, AUDITOR, etc.) are denied.
     *
     * @param actor The authenticated user executing the pack
     * @param transfer The stock transfer being packed
     * @throws AccessDeniedException if authorization fails
     */
    public void assertCanPackTransfer(User actor, StockTransfer transfer) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=PACK_TRANSFER");
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (transfer == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target transfer, requested_action=PACK_TRANSFER", actor.getId());
            throw new AccessDeniedException("Stock transfer is required");
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();
        UUID transferId = transfer.getId();

        // SUPER_ADMIN has global administrative authority
        if ("SUPER_ADMIN".equals(roleName)) {
            return;
        }

        if ("CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assignedWarehouse = actor.getAssignedWarehouse();
            if (assignedWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=PACK_TRANSFER, reason=No assigned warehouse",
                        actorId, transferId);
                throw new AccessDeniedException("Central warehouse manager has no assigned warehouse");
            }

            if (assignedWarehouse.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=PACK_TRANSFER, reason=Assigned warehouse is inactive",
                        actorId, assignedWarehouse.getId(), transferId);
                throw new AccessDeniedException("Central warehouse manager assigned warehouse is inactive");
            }

            Warehouse sourceWarehouse = transfer.getSourceWarehouse();
            if (sourceWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=PACK_TRANSFER, reason=Transfer source warehouse is missing",
                        actorId, transferId);
                throw new AccessDeniedException("Transfer source warehouse is missing");
            }

            if (!assignedWarehouse.getId().equals(sourceWarehouse.getId())) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, resource_warehouse={}, requested_action=PACK_TRANSFER, reason=Facility mismatch",
                        actorId, assignedWarehouse.getId(), transferId, sourceWarehouse.getId());
                throw new AccessDeniedException("Central warehouse manager is not assigned to source warehouse: " + sourceWarehouse.getId());
            }

            return;
        }

        // Fail-closed for all other roles (e.g. STORE_MANAGER, LOGISTICS_COORDINATOR, AUDITOR)
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=PACK_TRANSFER, reason=Unauthorized role for packing",
                actorId, roleName, transferId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to pack transfers");
    }

    /**
     * Enforces facility-level and role-level authorization for approving stock transfers.
     *
     * Invariant:
     * - SUPER_ADMIN has global administrative authorization.
     * - CENTRAL_WAREHOUSE_MANAGER may only approve transfers where transfer.sourceWarehouse
     *   matches their active assignedWarehouse.
     * - Missing/inactive warehouse assignments fail closed.
     * - Destination warehouse assignment does NOT grant approval authority.
     * - All other roles (STORE_MANAGER, LOGISTICS_COORDINATOR, AUDITOR, etc.) are denied.
     *
     * @param actor The authenticated user executing the approval
     * @param transfer The stock transfer being approved
     * @throws AccessDeniedException if authorization fails
     */
    public void assertCanApproveTransfer(User actor, StockTransfer transfer) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=APPROVE_TRANSFER");
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (transfer == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target transfer, requested_action=APPROVE_TRANSFER", actor.getId());
            throw new AccessDeniedException("Stock transfer is required");
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();
        UUID transferId = transfer.getId();

        // SUPER_ADMIN has global administrative authority
        if ("SUPER_ADMIN".equals(roleName)) {
            return;
        }

        if ("CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assignedWarehouse = actor.getAssignedWarehouse();
            if (assignedWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=APPROVE_TRANSFER, reason=No assigned warehouse",
                        actorId, transferId);
                throw new AccessDeniedException("Central warehouse manager has no assigned warehouse");
            }

            if (assignedWarehouse.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=APPROVE_TRANSFER, reason=Assigned warehouse is inactive",
                        actorId, assignedWarehouse.getId(), transferId);
                throw new AccessDeniedException("Central warehouse manager assigned warehouse is inactive");
            }

            Warehouse sourceWarehouse = transfer.getSourceWarehouse();
            if (sourceWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=APPROVE_TRANSFER, reason=Transfer source warehouse is missing",
                        actorId, transferId);
                throw new AccessDeniedException("Transfer source warehouse is missing");
            }

            if (!assignedWarehouse.getId().equals(sourceWarehouse.getId())) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, resource_warehouse={}, requested_action=APPROVE_TRANSFER, reason=Facility mismatch",
                        actorId, assignedWarehouse.getId(), transferId, sourceWarehouse.getId());
                throw new AccessDeniedException("Central warehouse manager is not assigned to source warehouse: " + sourceWarehouse.getId());
            }

            return;
        }

        // Fail-closed for all other roles (e.g. STORE_MANAGER, LOGISTICS_COORDINATOR, AUDITOR)
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=APPROVE_TRANSFER, reason=Unauthorized role for approval",
                actorId, roleName, transferId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to approve transfers");
    }

    /**
     * Enforces authorization for cancelling stock transfers.
     *
     * Invariant:
     * - SUPER_ADMIN has global administrative authorization.
     * - The original requester (transfer.requestedBy) may cancel their own transfer,
     *   provided they have an active assigned warehouse.
     * - CENTRAL_WAREHOUSE_MANAGER may cancel transfers where transfer.sourceWarehouse
     *   matches their active assignedWarehouse (the fulfilling warehouse committing stock).
     * - Missing/inactive warehouse assignments fail closed.
     * - Non-requester store managers, logistics coordinators, auditors, and foreign
     *   warehouse managers are denied.
     *
     * @param actor The authenticated user executing the cancellation
     * @param transfer The stock transfer being cancelled
     * @throws AccessDeniedException if authorization fails
     */
    public void assertCanCancelTransfer(User actor, StockTransfer transfer) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=CANCEL_TRANSFER");
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (transfer == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target transfer, requested_action=CANCEL_TRANSFER", actor.getId());
            throw new AccessDeniedException("Stock transfer is required");
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();
        UUID transferId = transfer.getId();

        // SUPER_ADMIN has global administrative authority
        if ("SUPER_ADMIN".equals(roleName)) {
            return;
        }

        // Fail-closed for roles with no cancellation authority (e.g. LOGISTICS_COORDINATOR, AUDITOR)
        if (!"CENTRAL_WAREHOUSE_MANAGER".equals(roleName) && !"STORE_MANAGER".equals(roleName)) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=CANCEL_TRANSFER, reason=Unauthorized role for cancellation",
                    actorId, roleName, transferId);
            throw new AccessDeniedException("User role " + roleName + " is not authorized to cancel transfers");
        }

        Warehouse assignedWarehouse = actor.getAssignedWarehouse();
        if (assignedWarehouse == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=CANCEL_TRANSFER, reason=No assigned warehouse",
                    actorId, roleName, transferId);
            throw new AccessDeniedException("User has no assigned warehouse");
        }

        if (assignedWarehouse.getStatus() != WarehouseStatus.ACTIVE) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=CANCEL_TRANSFER, reason=Assigned warehouse is inactive",
                    actorId, roleName, assignedWarehouse.getId(), transferId);
            throw new AccessDeniedException("User assigned warehouse is inactive");
        }

        // Requester may cancel their own transfer
        if (transfer.getRequestedBy() != null && actorId.equals(transfer.getRequestedBy().getId())) {
            return;
        }

        // Central Warehouse Manager of the source warehouse may cancel transfers originating from their facility
        if ("CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse sourceWarehouse = transfer.getSourceWarehouse();
            if (sourceWarehouse != null && assignedWarehouse.getId().equals(sourceWarehouse.getId())) {
                return;
            }

            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, resource_warehouse={}, requested_action=CANCEL_TRANSFER, reason=Facility mismatch",
                    actorId, assignedWarehouse.getId(), transferId, (sourceWarehouse != null ? sourceWarehouse.getId() : "null"));
            throw new AccessDeniedException("Central warehouse manager is not authorized to cancel foreign transfer: " + transferId);
        }

        // Fail-closed for non-requester STORE_MANAGER
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=CANCEL_TRANSFER, reason=Not requester",
                actorId, roleName, transferId);
        throw new AccessDeniedException("User is not authorized to cancel transfer: " + transferId);
    }
}
