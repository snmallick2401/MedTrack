package com.medtrack.shipment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.medtrack.auth.entity.Role;
import com.medtrack.inventory.service.InventoryMovementService;
import com.medtrack.shared.idempotency.IdempotencyService;
import com.medtrack.shipment.dto.ShipmentResponse;
import com.medtrack.shipment.entity.Shipment;
import com.medtrack.shipment.entity.ShipmentStatus;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.transfer.entity.StockTransfer;
import com.medtrack.transfer.entity.TransferStatus;
import com.medtrack.transfer.repository.StockTransferRepository;
import com.medtrack.user.entity.User;
import com.medtrack.user.entity.UserStatus;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.entity.Warehouse;
import com.medtrack.warehouse.entity.WarehouseStatus;
import com.medtrack.warehouse.entity.WarehouseType;
import com.medtrack.warehouse.repository.StorageLocationRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class ShipmentDispatchAuthorizationSecurityTest {

    private ShipmentAuthorizationService authService;
    private ShipmentService shipmentService;

    private ShipmentRepository shipmentRepo;
    private StockTransferRepository transferRepo;
    private UserRepository userRepo;
    private StorageLocationRepository locationRepo;
    private InventoryMovementService inventoryService;
    private IdempotencyService idempotencyService;
    private EntityManager em;

    private Warehouse warehouseA;
    private Warehouse warehouseB;
    private Warehouse inactiveWarehouse;

    private User centralManagerA;
    private User centralManagerB;
    private User centralManagerNoWarehouse;
    private User centralManagerInactiveWarehouse;
    private User superAdmin;
    private User storeManagerA;
    private User logisticsCoordinator;
    private User auditor;

    private StockTransfer transferFromAtoB;
    private StockTransfer transferFromBtoA;

    @BeforeEach
    void setUp() {
        authService = new ShipmentAuthorizationService();

        shipmentRepo = mock(ShipmentRepository.class);
        transferRepo = mock(StockTransferRepository.class);
        userRepo = mock(UserRepository.class);
        locationRepo = mock(StorageLocationRepository.class);
        inventoryService = mock(InventoryMovementService.class);
        idempotencyService = mock(IdempotencyService.class);
        em = mock(EntityManager.class);

        // Default mock behavior for idempotencyService: execute business action directly
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> action = invocation.getArgument(5);
            return action.get();
        });

        shipmentService = new ShipmentService(
                shipmentRepo,
                transferRepo,
                userRepo,
                locationRepo,
                inventoryService,
                idempotencyService,
                em,
                authService
        );

        // Warehouses
        warehouseA = createWarehouse("CW01", "Central Warehouse North", WarehouseStatus.ACTIVE);
        warehouseB = createWarehouse("CW02", "Central Warehouse South", WarehouseStatus.ACTIVE);
        inactiveWarehouse = createWarehouse("CW03", "Decommissioned Warehouse", WarehouseStatus.INACTIVE);

        // Roles
        Role roleCentralManager = createRole("CENTRAL_WAREHOUSE_MANAGER");
        Role roleStoreManager = createRole("STORE_MANAGER");
        Role roleSuperAdmin = createRole("SUPER_ADMIN");
        Role roleLogistics = createRole("LOGISTICS_COORDINATOR");
        Role roleAuditor = createRole("AUDITOR");

        // Users
        centralManagerA = createUser("cwm.a@medtrack.local", roleCentralManager, warehouseA);
        centralManagerB = createUser("cwm.b@medtrack.local", roleCentralManager, warehouseB);
        centralManagerNoWarehouse = createUser("cwm.nowh@medtrack.local", roleCentralManager, null);
        centralManagerInactiveWarehouse = createUser("cwm.inact@medtrack.local", roleCentralManager, inactiveWarehouse);
        superAdmin = createUser("admin@medtrack.local", roleSuperAdmin, null);
        storeManagerA = createUser("store.a@medtrack.local", roleStoreManager, warehouseA);
        logisticsCoordinator = createUser("logistics@medtrack.local", roleLogistics, null);
        auditor = createUser("auditor@medtrack.local", roleAuditor, null);

        // Transfers
        transferFromAtoB = createTransfer("TRF-2026-000101", warehouseA, warehouseB, centralManagerA, TransferStatus.PACKED);
        transferFromBtoA = createTransfer("TRF-2026-000102", warehouseB, warehouseA, centralManagerB, TransferStatus.PACKED);
    }

    @Test
    @DisplayName("Test 1: Authorized source warehouse manager can dispatch transfer")
    void authorizedCentralWarehouseManagerCanDispatch() {
        assertDoesNotThrow(() -> authService.assertCanDispatchTransfer(centralManagerA, transferFromAtoB));
    }

    @Test
    @DisplayName("Test 2: Central Warehouse Manager A dispatching transfer originating from Warehouse B is DENIED (BOLA horizontal)")
    void crossFacilityDispatchIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanDispatchTransfer(centralManagerA, transferFromBtoA)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"),
                "Exception must indicate source facility mismatch: " + ex.getMessage());
    }

    @Test
    @DisplayName("Test 3: Central Warehouse Manager B dispatching transfer originating from Warehouse A is DENIED (Reverse BOLA)")
    void reverseCrossFacilityDispatchIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanDispatchTransfer(centralManagerB, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 4: Central Warehouse Manager with no assigned warehouse fails closed")
    void managerWithNoAssignedWarehouseFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanDispatchTransfer(centralManagerNoWarehouse, transferFromAtoB)
        );
        assertEquals("Central warehouse manager has no assigned warehouse", ex.getMessage());
    }

    @Test
    @DisplayName("Test 5: Central Warehouse Manager with inactive assigned warehouse fails closed")
    void managerWithInactiveAssignedWarehouseFailsClosed() {
        StockTransfer transferFromInactive = createTransfer("TRF-2026-000103", inactiveWarehouse, warehouseA, centralManagerInactiveWarehouse, TransferStatus.PACKED);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanDispatchTransfer(centralManagerInactiveWarehouse, transferFromInactive)
        );
        assertEquals("Central warehouse manager assigned warehouse is inactive", ex.getMessage());
    }

    @Test
    @DisplayName("Test 6: SUPER_ADMIN has global administrative dispatch authority")
    void superAdminPolicyAllowsGlobalDispatch() {
        assertDoesNotThrow(() -> authService.assertCanDispatchTransfer(superAdmin, transferFromAtoB));
        assertDoesNotThrow(() -> authService.assertCanDispatchTransfer(superAdmin, transferFromBtoA));
    }

    @Test
    @DisplayName("Test 7: STORE_MANAGER is denied dispatch authority even at own facility")
    void storeManagerDeniedDispatch() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanDispatchTransfer(storeManagerA, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to dispatch transfers"));
    }

    @Test
    @DisplayName("Test 8: LOGISTICS_COORDINATOR retains global freight dispatch coordination authority")
    void logisticsCoordinatorAllowsGlobalFreightDispatch() {
        assertDoesNotThrow(() -> authService.assertCanDispatchTransfer(logisticsCoordinator, transferFromAtoB));
        assertDoesNotThrow(() -> authService.assertCanDispatchTransfer(logisticsCoordinator, transferFromBtoA));
    }

    @Test
    @DisplayName("Test 9: Destination warehouse assignment does NOT grant dispatch authority")
    void destinationWarehouseManagerCannotDispatchSourceTransfer() {
        // Manager B is assigned to warehouseB, which is the DESTINATION of transferFromAtoB
        assertEquals(warehouseB.getId(), transferFromAtoB.getDestinationWarehouse().getId());
        assertEquals(warehouseB.getId(), centralManagerB.getAssignedWarehouse().getId());

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanDispatchTransfer(centralManagerB, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 10: Server derives source warehouse from persisted entity, ignoring client assertions")
    void clientControlledSourceCannotBypassPersistedSourceWarehouse() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> shipmentService.dispatch(centralManagerA.getId(), transferFromBtoA.getId(), null)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));

        // Verify zero downstream mutation
        verify(shipmentRepo, never()).lockByTransferId(any());
        verify(inventoryService, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("Test 11: No partial mutation: Denied dispatch performs ZERO state or inventory mutations")
    void noPartialCommitOnUnauthorizedDispatch() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        Shipment mockShipment = mock(Shipment.class);
        when(mockShipment.getStatus()).thenReturn(ShipmentStatus.PREPARING);
        when(shipmentRepo.lockByTransferId(transferFromBtoA.getId())).thenReturn(Optional.of(mockShipment));

        assertThrows(
                AccessDeniedException.class,
                () -> shipmentService.dispatch(centralManagerA.getId(), transferFromBtoA.getId(), null)
        );

        // Verify transfer status remains PACKED
        assertEquals(TransferStatus.PACKED, transferFromBtoA.getStatus(), "Transfer status must not transition to DISPATCHED");
        // Verify shipment dispatch never called
        verify(mockShipment, never()).dispatch();
        // Verify inventory movement never called
        verify(inventoryService, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("Test 12: Concurrency safety: Concurrent cross-facility requests cannot bypass authorization")
    void concurrencySafetyUnderLoad() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger deniedCount = new AtomicInteger(0);
        AtomicInteger authorizedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final boolean isUnauthorized = (i % 2 == 0); // half unauthorized Manager B, half authorized Manager A
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    User actor = isUnauthorized ? centralManagerB : centralManagerA;
                    authService.assertCanDispatchTransfer(actor, transferFromAtoB);
                    authorizedCount.incrementAndGet();
                } catch (AccessDeniedException e) {
                    deniedCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Unexpected exception: " + e.getMessage());
                }
            }));
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(10, deniedCount.get(), "Exactly 10 unauthorized requests must be rejected");
        assertEquals(10, authorizedCount.get(), "Exactly 10 authorized requests must pass authorization");
    }

    @Test
    @DisplayName("Test 13: Duplicate dispatch: Authorized user receives idempotent response, unauthorized user remains blocked")
    void duplicateDispatchHandling() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));

        StockTransfer dispatchedTransfer = createTransfer("TRF-2026-000104", warehouseA, warehouseB, centralManagerA, TransferStatus.DISPATCHED);
        when(transferRepo.lockById(dispatchedTransfer.getId())).thenReturn(Optional.of(dispatchedTransfer));

        Shipment dispatchedShipment = mock(Shipment.class);
        when(dispatchedShipment.getId()).thenReturn(UUID.randomUUID());
        when(dispatchedShipment.getStatus()).thenReturn(ShipmentStatus.DISPATCHED);
        when(dispatchedShipment.getShipmentNumber()).thenReturn("SHP-2026-000104");
        when(dispatchedShipment.getTrackingNumber()).thenReturn("TRK-2026-000104");
        when(dispatchedShipment.getTransfer()).thenReturn(dispatchedTransfer);
        when(dispatchedShipment.getOrigin()).thenReturn(warehouseA);
        when(dispatchedShipment.getDestination()).thenReturn(warehouseB);
        when(dispatchedShipment.getCarrierName()).thenReturn("PharmaLogistics");
        when(dispatchedShipment.getEstimatedArrival()).thenReturn(java.time.Instant.now());
        when(dispatchedShipment.getItems()).thenReturn(List.of());
        when(shipmentRepo.lockByTransferId(dispatchedTransfer.getId())).thenReturn(Optional.of(dispatchedShipment));

        // 1. Authorized caller re-dispatching gets idempotent response without re-mutating inventory
        ShipmentResponse res = shipmentService.dispatch(centralManagerA.getId(), dispatchedTransfer.getId(), "KEY-DUP-1");
        assertNotNull(res);
        assertEquals("DISPATCHED", res.status());
        verify(inventoryService, never()).dispatch(any(), any(), any());

        // 2. Unauthorized caller re-dispatching is DENIED fail-closed
        assertThrows(
                AccessDeniedException.class,
                () -> shipmentService.dispatch(centralManagerB.getId(), dispatchedTransfer.getId(), "KEY-DUP-2")
        );
    }

    @Test
    @DisplayName("Test 14: Already-dispatched transfer denied to unauthorized actor (authorization precedes state short-circuits)")
    void alreadyDispatchedTransferDeniedToUnauthorizedActor() {
        StockTransfer dispatchedTransfer = createTransfer("TRF-2026-000105", warehouseA, warehouseB, centralManagerA, TransferStatus.DISPATCHED);
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));
        when(transferRepo.lockById(dispatchedTransfer.getId())).thenReturn(Optional.of(dispatchedTransfer));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> shipmentService.dispatch(centralManagerB.getId(), dispatchedTransfer.getId(), null)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
        // Confirm shipment was never even locked
        verify(shipmentRepo, never()).lockByTransferId(any());
    }

    @Test
    @DisplayName("Test 15: Direct API attack replay: Central Warehouse Manager requesting foreign dispatch is denied with 403")
    void directApiAttackReplay_returns403() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> shipmentService.dispatch(centralManagerA.getId(), transferFromBtoA.getId(), "KEY-ATTACK-01")
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
        verify(inventoryService, never()).dispatch(any(), any(), any());
    }

    // --- Helper Methods ---

    private Warehouse createWarehouse(String code, String name, WarehouseStatus status) {
        Warehouse w = new Warehouse(code, name, WarehouseType.CENTRAL_WAREHOUSE, "Address " + code,
                BigDecimal.valueOf(12.34), BigDecimal.valueOf(56.78), "+1-555-0100", status);
        ReflectionTestUtils.setField(w, "id", UUID.randomUUID());
        return w;
    }

    private Role createRole(String name) {
        try {
            Constructor<Role> c = Role.class.getDeclaredConstructor();
            c.setAccessible(true);
            Role r = c.newInstance();
            ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(r, "name", name);
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User createUser(String email, Role role, Warehouse warehouse) {
        try {
            Constructor<User> c = User.class.getDeclaredConstructor();
            c.setAccessible(true);
            User u = c.newInstance();
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(u, "email", email);
            ReflectionTestUtils.setField(u, "fullName", "Test User " + email);
            ReflectionTestUtils.setField(u, "passwordHash", "hashed");
            ReflectionTestUtils.setField(u, "role", role);
            ReflectionTestUtils.setField(u, "assignedWarehouse", warehouse);
            ReflectionTestUtils.setField(u, "status", UserStatus.ACTIVE);
            return u;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private StockTransfer createTransfer(String number, Warehouse source, Warehouse dest, User requester, TransferStatus status) {
        StockTransfer t = new StockTransfer(number, source, dest, requester, "Transfer notes");
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(t, "status", status);
        return t;
    }
}
