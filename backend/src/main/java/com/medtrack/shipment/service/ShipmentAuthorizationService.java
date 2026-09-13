package com.medtrack.shipment.service;

import com.medtrack.auth.entity.Role;
import com.medtrack.shipment.entity.Shipment;
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
public class ShipmentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentAuthorizationService.class);

    /**
     * Determines whether the user holds a role with global shipment read visibility.
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
     * Resolves the warehouse ID that scopes the user's shipment queries.
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
     * Enforces facility-level and role-level authorization for receiving a stock transfer shipment.
     *
     * Invariant:
     * - SUPER_ADMIN has global administrative authorization.
     * - STORE_MANAGER may only receive transfers where transfer.destinationWarehouse matches their active assignedWarehouse.
     * - Missing/inactive warehouse assignments fail closed.
     * - All other roles are denied.
     *
     * @param actor The authenticated user executing the receipt
     * @param transfer The stock transfer being received
     * @throws AccessDeniedException if authorization fails
     */
    public void assertCanReceiveTransfer(User actor, StockTransfer transfer) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=RECEIVE_TRANSFER");
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (transfer == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target transfer, requested_action=RECEIVE_TRANSFER", actor.getId());
            throw new AccessDeniedException("Stock transfer is required");
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;

        // SUPER_ADMIN has global administrative authority
        if ("SUPER_ADMIN".equals(roleName)) {
            return;
        }

        UUID actorId = actor.getId();
        UUID transferId = transfer.getId();

        if ("STORE_MANAGER".equals(roleName)) {
            Warehouse assignedWarehouse = actor.getAssignedWarehouse();
            if (assignedWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=STORE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=RECEIVE_TRANSFER, reason=No assigned warehouse",
                        actorId, transferId);
                throw new AccessDeniedException("Store manager has no assigned warehouse");
            }

            if (assignedWarehouse.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=STORE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=RECEIVE_TRANSFER, reason=Assigned warehouse is inactive",
                        actorId, assignedWarehouse.getId(), transferId);
                throw new AccessDeniedException("Store manager assigned warehouse is inactive");
            }

            Warehouse destinationWarehouse = transfer.getDestinationWarehouse();
            if (destinationWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=STORE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=RECEIVE_TRANSFER, reason=Transfer destination warehouse is missing",
                        actorId, transferId);
                throw new AccessDeniedException("Transfer destination warehouse is missing");
            }

            if (!assignedWarehouse.getId().equals(destinationWarehouse.getId())) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=STORE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, resource_warehouse={}, requested_action=RECEIVE_TRANSFER, reason=Facility mismatch",
                        actorId, assignedWarehouse.getId(), transferId, destinationWarehouse.getId());
                throw new AccessDeniedException("Store manager is not assigned to destination warehouse: " + destinationWarehouse.getId());
            }

            return;
        }

        // Fail-closed for all other roles
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=RECEIVE_TRANSFER, reason=Unauthorized role for receipt",
                actorId, roleName, transferId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to receive transfers");
    }

    /**
     * Enforces facility-level authorization for viewing a specific shipment.
     *
     * Invariant:
     * - SUPER_ADMIN, AUDITOR, and LOGISTICS_COORDINATOR have global read visibility.
     * - STORE_MANAGER and CENTRAL_WAREHOUSE_MANAGER may view shipments where their active
     *   assigned warehouse is either the origin or destination facility.
     * - Mismatches or missing/inactive facility associations fail closed.
     */
    public void assertCanViewShipment(User actor, Shipment shipment) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=VIEW_SHIPMENT");
            throw new AccessDeniedException("Authenticated actor is required");
        }
        if (shipment == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target shipment, requested_action=VIEW_SHIPMENT", actor.getId());
            throw new AccessDeniedException("Shipment is required");
        }

        if (isGlobalVisibilityRole(actor)) {
            return;
        }

        Role role = actor.getRole();
        String roleName = (role != null) ? role.getName() : null;
        UUID actorId = actor.getId();
        UUID shipmentId = shipment.getId();

        if ("STORE_MANAGER".equals(roleName) || "CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assigned = actor.getAssignedWarehouse();
            if (assigned == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=Shipment, resource_id={}, requested_action=VIEW_SHIPMENT, reason=No assigned warehouse",
                        actorId, roleName, shipmentId);
                throw new AccessDeniedException("User has no assigned warehouse");
            }
            if (assigned.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, resource_type=Shipment, resource_id={}, requested_action=VIEW_SHIPMENT, reason=Assigned warehouse is inactive",
                        actorId, roleName, assigned.getId(), shipmentId);
                throw new AccessDeniedException("User assigned warehouse is inactive");
            }

            boolean isOrigin = shipment.getOrigin() != null && assigned.getId().equals(shipment.getOrigin().getId());
            boolean isDestination = shipment.getDestination() != null && assigned.getId().equals(shipment.getDestination().getId());

            if (isOrigin || isDestination) {
                return;
            }

            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, actor_warehouse={}, resource_type=Shipment, resource_id={}, requested_action=VIEW_SHIPMENT, reason=Facility mismatch",
                    actorId, roleName, assigned.getId(), shipmentId);
            throw new AccessDeniedException("User is not authorized to view shipment outside assigned facility");
        }

        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=Shipment, resource_id={}, requested_action=VIEW_SHIPMENT, reason=Unauthorized role for viewing shipment",
                actorId, roleName, shipmentId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to view shipments");
    }

    /**
     * Enforces facility-level and role-level authorization for dispatching a stock transfer.
     *
     * Invariant:
     * - SUPER_ADMIN has global administrative authorization.
     * - LOGISTICS_COORDINATOR has global freight and carrier dispatch authority.
     * - CENTRAL_WAREHOUSE_MANAGER may only dispatch transfers where transfer.sourceWarehouse
     *   matches their active assignedWarehouse.
     * - Missing/inactive warehouse assignments fail closed.
     * - Destination warehouse assignment does NOT grant dispatch authority.
     * - All other roles (STORE_MANAGER, AUDITOR, etc.) are denied.
     *
     * @param actor The authenticated user executing the dispatch
     * @param transfer The stock transfer being dispatched
     * @throws AccessDeniedException if authorization fails
     */
    public void assertCanDispatchTransfer(User actor, StockTransfer transfer) {
        if (actor == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing actor principal, requested_action=DISPATCH_TRANSFER");
            throw new AccessDeniedException("Authenticated actor is required");
        }

        if (transfer == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, reason=Missing target transfer, requested_action=DISPATCH_TRANSFER", actor.getId());
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

        // LOGISTICS_COORDINATOR has global freight & carrier dispatch authority across all facilities
        if ("LOGISTICS_COORDINATOR".equals(roleName)) {
            return;
        }

        if ("CENTRAL_WAREHOUSE_MANAGER".equals(roleName)) {
            Warehouse assignedWarehouse = actor.getAssignedWarehouse();
            if (assignedWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=DISPATCH_TRANSFER, reason=No assigned warehouse",
                        actorId, transferId);
                throw new AccessDeniedException("Central warehouse manager has no assigned warehouse");
            }

            if (assignedWarehouse.getStatus() != WarehouseStatus.ACTIVE) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, requested_action=DISPATCH_TRANSFER, reason=Assigned warehouse is inactive",
                        actorId, assignedWarehouse.getId(), transferId);
                throw new AccessDeniedException("Central warehouse manager assigned warehouse is inactive");
            }

            Warehouse sourceWarehouse = transfer.getSourceWarehouse();
            if (sourceWarehouse == null) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, resource_type=StockTransfer, resource_id={}, requested_action=DISPATCH_TRANSFER, reason=Transfer source warehouse is missing",
                        actorId, transferId);
                throw new AccessDeniedException("Transfer source warehouse is missing");
            }

            if (!assignedWarehouse.getId().equals(sourceWarehouse.getId())) {
                log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role=CENTRAL_WAREHOUSE_MANAGER, actor_warehouse={}, resource_type=StockTransfer, resource_id={}, resource_warehouse={}, requested_action=DISPATCH_TRANSFER, reason=Facility mismatch",
                        actorId, assignedWarehouse.getId(), transferId, sourceWarehouse.getId());
                throw new AccessDeniedException("Central warehouse manager is not assigned to source warehouse: " + sourceWarehouse.getId());
            }

            return;
        }

        // Fail-closed for all other roles (e.g. STORE_MANAGER, AUDITOR)
        log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, actor_id={}, actor_role={}, resource_type=StockTransfer, resource_id={}, requested_action=DISPATCH_TRANSFER, reason=Unauthorized role for dispatch",
                actorId, roleName, transferId);
        throw new AccessDeniedException("User role " + roleName + " is not authorized to dispatch transfers");
    }

    /**
     * Enforces authorization for dispatching a shipment by validating dispatch authority on its underlying transfer.
     */
    public void assertCanDispatchShipment(User actor, Shipment shipment) {
        if (shipment == null) {
            log.warn("SECURITY_EVENT: event_type=AUTHORIZATION_DENIED, reason=Missing target shipment, requested_action=DISPATCH_SHIPMENT");
            throw new AccessDeniedException("Shipment is required");
        }
        assertCanDispatchTransfer(actor, shipment.getTransfer());
    }
}
