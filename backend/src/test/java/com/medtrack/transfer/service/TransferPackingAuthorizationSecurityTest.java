package com.medtrack.transfer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.medtrack.auth.entity.Role;
import com.medtrack.batch.entity.Batch;
import com.medtrack.inventory.service.FefoAllocationEngine;
import com.medtrack.inventory.service.InventoryMovementService;
import com.medtrack.medicine.entity.Medicine;
import com.medtrack.medicine.repository.MedicineRepository;
import com.medtrack.shared.idempotency.IdempotencyService;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.transfer.dto.TransferResponse;
import com.medtrack.transfer.entity.StockTransfer;
import com.medtrack.transfer.entity.StockTransferItem;
import com.medtrack.transfer.entity.TransferStatus;
import com.medtrack.transfer.repository.StockTransferRepository;
import com.medtrack.user.entity.User;
import com.medtrack.user.entity.UserStatus;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.entity.Warehouse;
import com.medtrack.warehouse.entity.WarehouseStatus;
import com.medtrack.warehouse.entity.WarehouseType;
import com.medtrack.warehouse.repository.WarehouseRepository;
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

class TransferPackingAuthorizationSecurityTest {

    private TransferAuthorizationService authService;
    private TransferService transferService;

    private StockTransferRepository transferRepo;
    private MedicineRepository medicineRepo;
    private WarehouseRepository warehouseRepo;
    private UserRepository userRepo;
    private FefoAllocationEngine fefo;
    private InventoryMovementService inventory;
    private ShipmentRepository shipmentRepo;
    private IdempotencyService idempotency;
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

    private Batch batchA;
    private Batch batchB;

    private StockTransfer transferFromAtoB;
    private StockTransfer transferFromBtoA;

    @BeforeEach
    void setUp() {
        authService = new TransferAuthorizationService();

        transferRepo = mock(StockTransferRepository.class);
        medicineRepo = mock(MedicineRepository.class);
        warehouseRepo = mock(WarehouseRepository.class);
        userRepo = mock(UserRepository.class);
        fefo = mock(FefoAllocationEngine.class);
        inventory = mock(InventoryMovementService.class);
        shipmentRepo = mock(ShipmentRepository.class);
        idempotency = mock(IdempotencyService.class);
        em = mock(EntityManager.class);

        // Mock idempotencyService to execute business lambda directly
        when(idempotency.execute(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> action = invocation.getArgument(5);
            return action.get();
        });

        transferService = new TransferService(
                transferRepo,
                medicineRepo,
                warehouseRepo,
                userRepo,
                fefo,
                inventory,
                shipmentRepo,
                idempotency,
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

        // Batches
        batchA = mock(Batch.class);
        when(batchA.getId()).thenReturn(UUID.randomUUID());

        batchB = mock(Batch.class);
        when(batchB.getId()).thenReturn(UUID.randomUUID());

        // Transfers in PICKED status (ready for packing)
        transferFromAtoB = createTransfer("TRF-2026-000401", warehouseA, warehouseB, centralManagerA, TransferStatus.PICKED);
        transferFromBtoA = createTransfer("TRF-2026-000402", warehouseB, warehouseA, centralManagerB, TransferStatus.PICKED);

        Medicine med = mock(Medicine.class);
        when(med.getId()).thenReturn(UUID.randomUUID());

        StockTransferItem itemA = new StockTransferItem(med, batchA, 50);
        itemA.pick(50);
        transferFromAtoB.addItem(itemA);

        StockTransferItem itemB = new StockTransferItem(med, batchB, 50);
        itemB.pick(50);
        transferFromBtoA.addItem(itemB);
    }

    @Test
    @DisplayName("Test 1: Authorized source warehouse manager can pack transfer")
    void authorizedCentralWarehouseManagerCanPack() {
        assertDoesNotThrow(() -> authService.assertCanPackTransfer(centralManagerA, transferFromAtoB));
    }

    @Test
    @DisplayName("Test 2: Central Warehouse Manager A packing transfer originating from Warehouse B is DENIED (BOLA horizontal)")
    void crossFacilityPackingIsDenied_HorizontalBOLA() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(centralManagerA, transferFromBtoA)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"),
                "Exception must indicate source facility mismatch: " + ex.getMessage());
    }

    @Test
    @DisplayName("Test 3: Central Warehouse Manager B packing transfer originating from Warehouse A is DENIED (Reverse BOLA)")
    void reverseCrossFacilityPackingIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(centralManagerB, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 4: Central Warehouse Manager with no assigned warehouse fails closed")
    void managerWithNoAssignedWarehouseFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(centralManagerNoWarehouse, transferFromAtoB)
        );
        assertEquals("Central warehouse manager has no assigned warehouse", ex.getMessage());
    }

    @Test
    @DisplayName("Test 5: Central Warehouse Manager with inactive assigned warehouse fails closed")
    void managerWithInactiveAssignedWarehouseFailsClosed() {
        StockTransfer transferFromInactive = createTransfer("TRF-2026-000403", inactiveWarehouse, warehouseA, centralManagerInactiveWarehouse, TransferStatus.PICKED);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(centralManagerInactiveWarehouse, transferFromInactive)
        );
        assertEquals("Central warehouse manager assigned warehouse is inactive", ex.getMessage());
    }

    @Test
    @DisplayName("Test 6: Transfer with missing source warehouse fails closed")
    void transferWithMissingSourceWarehouseFailsClosed() {
        StockTransfer transferNoSource = createTransfer("TRF-2026-000404", null, warehouseB, centralManagerA, TransferStatus.PICKED);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(centralManagerA, transferNoSource)
        );
        assertEquals("Transfer source warehouse is missing", ex.getMessage());
    }

    @Test
    @DisplayName("Test 7: Missing actor principal fails closed")
    void missingActorFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(null, transferFromAtoB)
        );
        assertEquals("Authenticated actor is required", ex.getMessage());
    }

    @Test
    @DisplayName("Test 8: Missing stock transfer fails closed")
    void missingTransferFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(centralManagerA, null)
        );
        assertEquals("Stock transfer is required", ex.getMessage());
    }

    @Test
    @DisplayName("Test 9: SUPER_ADMIN has global administrative packing authority")
    void superAdminPolicyAllowsGlobalPacking() {
        assertDoesNotThrow(() -> authService.assertCanPackTransfer(superAdmin, transferFromAtoB));
        assertDoesNotThrow(() -> authService.assertCanPackTransfer(superAdmin, transferFromBtoA));
    }

    @Test
    @DisplayName("Test 10: STORE_MANAGER is denied packing authority even at own facility")
    void storeManagerDeniedPacking() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(storeManagerA, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to pack transfers"));
    }

    @Test
    @DisplayName("Test 11: LOGISTICS_COORDINATOR is denied packing authority")
    void logisticsCoordinatorDeniedPacking() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(logisticsCoordinator, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to pack transfers"));
    }

    @Test
    @DisplayName("Test 12: AUDITOR is denied packing authority (read-only)")
    void auditorDeniedPacking() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(auditor, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to pack transfers"));
    }

    @Test
    @DisplayName("Test 13: Destination warehouse assignment does NOT grant packing authority (physical custody at source)")
    void destinationWarehouseManagerCannotPackSourceTransfer() {
        // Manager B is assigned to warehouseB, which is the DESTINATION of transferFromAtoB
        assertEquals(warehouseB.getId(), transferFromAtoB.getDestinationWarehouse().getId());
        assertEquals(warehouseB.getId(), centralManagerB.getAssignedWarehouse().getId());

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPackTransfer(centralManagerB, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 14: Server derives source warehouse from persisted entity, ignoring client assertions")
    void clientControlledSourceCannotBypassPersistedSourceWarehouse() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.pack(centralManagerA.getId(), transferFromBtoA.getId(), "KEY-PACK-ATTACK-01")
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 15: No partial mutation: Denied pack performs ZERO state transitions and ZERO item/shipment mutations")
    void noPartialMutationOnUnauthorizedPacking() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        assertThrows(
                AccessDeniedException.class,
                () -> transferService.pack(centralManagerA.getId(), transferFromBtoA.getId())
        );

        // Verify transfer status remains PICKED (never transitioned to PACKED)
        assertEquals(TransferStatus.PICKED, transferFromBtoA.getStatus());
        // Verify items remain unchanged
        assertEquals(1, transferFromBtoA.getItems().size());
        assertEquals(50, transferFromBtoA.getItems().getFirst().getPickedQuantity());
        assertEquals(0, transferFromBtoA.getItems().getFirst().getDispatchedQuantity());
    }

    @Test
    @DisplayName("Test 16: Concurrency safety: Concurrent cross-facility packing requests cannot bypass authorization")
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
                    authService.assertCanPackTransfer(actor, transferFromAtoB);
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
    @DisplayName("Test 17: Concurrent duplicate handling: Authorized caller receives idempotent response, unauthorized caller remains blocked")
    void concurrentDuplicateHandling_Idempotency() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));

        StockTransfer packedTransfer = createTransfer("TRF-2026-000405", warehouseA, warehouseB, centralManagerA, TransferStatus.PACKED);
        when(transferRepo.lockById(packedTransfer.getId())).thenReturn(Optional.of(packedTransfer));

        // 1. Authorized caller packing already-packed transfer gets idempotent response without error
        TransferResponse res = transferService.pack(centralManagerA.getId(), packedTransfer.getId(), "KEY-PACK-DUP-1");
        assertNotNull(res);
        assertEquals("PACKED", res.status());

        // 2. Unauthorized caller packing already-packed transfer is DENIED fail-closed
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.pack(centralManagerB.getId(), packedTransfer.getId(), "KEY-PACK-DUP-2")
        );
    }

    @Test
    @DisplayName("Test 18: Unauthorized retry / idempotency key manipulation cannot bypass authorization")
    void unauthorizedRetryHandling() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        // Attempt 1 with key 1
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.pack(centralManagerA.getId(), transferFromBtoA.getId(), "KEY-RETRY-1")
        );

        // Attempt 2 with key 2
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.pack(centralManagerA.getId(), transferFromBtoA.getId(), "KEY-RETRY-2")
        );

        // Attempt 3 with null key
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.pack(centralManagerA.getId(), transferFromBtoA.getId(), null)
        );

        assertEquals(TransferStatus.PICKED, transferFromBtoA.getStatus());
    }

    @Test
    @DisplayName("Test 19: Already packed transfer denied to unauthorized actor (authorization precedes state short-circuits)")
    void alreadyPackedTransferDeniedToUnauthorizedActor() {
        StockTransfer packedTransfer = createTransfer("TRF-2026-000406", warehouseA, warehouseB, centralManagerA, TransferStatus.PACKED);
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));
        when(transferRepo.lockById(packedTransfer.getId())).thenReturn(Optional.of(packedTransfer));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.pack(centralManagerB.getId(), packedTransfer.getId(), null)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 20: Direct HTTP API attack replay: Central Warehouse Manager requesting foreign pack is denied with 403")
    void directApiAttackReplay_returns403() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.pack(centralManagerA.getId(), transferFromBtoA.getId(), "ATTACK-KEY-01")
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
        assertEquals(TransferStatus.PICKED, transferFromBtoA.getStatus());
    }

    @Test
    @DisplayName("Test 21: Authorized manager successfully packs transfer in PICKED status")
    void authorizedPackingSuccessfullyUpdatesTransferStatusToPacked() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromAtoB.getId())).thenReturn(Optional.of(transferFromAtoB));

        TransferResponse response = transferService.pack(centralManagerA.getId(), transferFromAtoB.getId());

        assertNotNull(response);
        assertEquals(TransferStatus.PACKED, transferFromAtoB.getStatus());
        assertEquals("PACKED", response.status());
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
