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
import com.medtrack.shared.exception.DomainException;
import com.medtrack.shared.idempotency.IdempotencyService;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.transfer.dto.PickRequest;
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

class TransferPickingAuthorizationSecurityTest {

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
        UUID batchAId = UUID.randomUUID();
        when(batchA.getId()).thenReturn(batchAId);

        batchB = mock(Batch.class);
        UUID batchBId = UUID.randomUUID();
        when(batchB.getId()).thenReturn(batchBId);

        // Transfers in ALLOCATED status (ready for picking)
        transferFromAtoB = createTransfer("TRF-2026-000301", warehouseA, warehouseB, centralManagerA, TransferStatus.ALLOCATED);
        transferFromBtoA = createTransfer("TRF-2026-000302", warehouseB, warehouseA, centralManagerB, TransferStatus.ALLOCATED);

        // Add allocated items to transfers
        Medicine med = mock(Medicine.class);
        when(med.getId()).thenReturn(UUID.randomUUID());
        transferFromAtoB.addItem(new StockTransferItem(med, batchA, 50));
        transferFromBtoA.addItem(new StockTransferItem(med, batchB, 50));
    }

    @Test
    @DisplayName("Test 1: Authorized source warehouse manager can pick transfer items")
    void authorizedCentralWarehouseManagerCanPick() {
        assertDoesNotThrow(() -> authService.assertCanPickTransfer(centralManagerA, transferFromAtoB));
    }

    @Test
    @DisplayName("Test 2: Central Warehouse Manager A picking transfer originating from Warehouse B is DENIED (BOLA horizontal)")
    void crossFacilityPickingIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(centralManagerA, transferFromBtoA)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"),
                "Exception must indicate source facility mismatch: " + ex.getMessage());
    }

    @Test
    @DisplayName("Test 3: Central Warehouse Manager B picking transfer originating from Warehouse A is DENIED (Reverse BOLA)")
    void reverseCrossFacilityPickingIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(centralManagerB, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 4: Central Warehouse Manager with no assigned warehouse fails closed")
    void managerWithNoAssignedWarehouseFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(centralManagerNoWarehouse, transferFromAtoB)
        );
        assertEquals("Central warehouse manager has no assigned warehouse", ex.getMessage());
    }

    @Test
    @DisplayName("Test 5: Central Warehouse Manager with inactive assigned warehouse fails closed")
    void managerWithInactiveAssignedWarehouseFailsClosed() {
        StockTransfer transferFromInactive = createTransfer("TRF-2026-000303", inactiveWarehouse, warehouseA, centralManagerInactiveWarehouse, TransferStatus.ALLOCATED);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(centralManagerInactiveWarehouse, transferFromInactive)
        );
        assertEquals("Central warehouse manager assigned warehouse is inactive", ex.getMessage());
    }

    @Test
    @DisplayName("Test 6: Transfer with missing source warehouse fails closed")
    void transferWithMissingSourceWarehouseFailsClosed() {
        StockTransfer transferNoSource = createTransfer("TRF-2026-000304", null, warehouseB, centralManagerA, TransferStatus.ALLOCATED);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(centralManagerA, transferNoSource)
        );
        assertEquals("Transfer source warehouse is missing", ex.getMessage());
    }

    @Test
    @DisplayName("Test 7: Missing actor principal fails closed")
    void missingActorFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(null, transferFromAtoB)
        );
        assertEquals("Authenticated actor is required", ex.getMessage());
    }

    @Test
    @DisplayName("Test 8: Missing stock transfer fails closed")
    void missingTransferFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(centralManagerA, null)
        );
        assertEquals("Stock transfer is required", ex.getMessage());
    }

    @Test
    @DisplayName("Test 9: SUPER_ADMIN has global administrative picking authority")
    void superAdminPolicyAllowsGlobalPicking() {
        assertDoesNotThrow(() -> authService.assertCanPickTransfer(superAdmin, transferFromAtoB));
        assertDoesNotThrow(() -> authService.assertCanPickTransfer(superAdmin, transferFromBtoA));
    }

    @Test
    @DisplayName("Test 10: STORE_MANAGER is denied picking authority even at own facility")
    void storeManagerDeniedPicking() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(storeManagerA, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to pick transfers"));
    }

    @Test
    @DisplayName("Test 11: LOGISTICS_COORDINATOR is denied picking authority")
    void logisticsCoordinatorDeniedPicking() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(logisticsCoordinator, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to pick transfers"));
    }

    @Test
    @DisplayName("Test 12: AUDITOR is denied picking authority (read-only)")
    void auditorDeniedPicking() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(auditor, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to pick transfers"));
    }

    @Test
    @DisplayName("Test 13: Destination warehouse assignment does NOT grant picking authority (physical custody at source)")
    void destinationWarehouseManagerCannotPickSourceTransfer() {
        // Manager B is assigned to warehouseB, which is the DESTINATION of transferFromAtoB
        assertEquals(warehouseB.getId(), transferFromAtoB.getDestinationWarehouse().getId());
        assertEquals(warehouseB.getId(), centralManagerB.getAssignedWarehouse().getId());

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanPickTransfer(centralManagerB, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 14: Server derives source warehouse from persisted entity, ignoring client assertions")
    void clientControlledSourceCannotBypassPersistedSourceWarehouse() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        PickRequest pickReq = new PickRequest(List.of(new PickRequest.Item(batchB.getId(), 50)));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.pick(centralManagerA.getId(), transferFromBtoA.getId(), pickReq)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 15: No partial mutation: Denied pick performs ZERO state transitions and ZERO item picking mutations")
    void noPartialMutationOnUnauthorizedPicking() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        PickRequest pickReq = new PickRequest(List.of(new PickRequest.Item(batchB.getId(), 50)));

        assertThrows(
                AccessDeniedException.class,
                () -> transferService.pick(centralManagerA.getId(), transferFromBtoA.getId(), pickReq)
        );

        // Verify transfer status remains ALLOCATED (never transitioned to PICKED)
        assertEquals(TransferStatus.ALLOCATED, transferFromBtoA.getStatus());

        // Verify items pickedQuantity remains 0
        assertEquals(1, transferFromBtoA.getItems().size());
        assertEquals(0, transferFromBtoA.getItems().getFirst().getPickedQuantity());
        assertEquals(50, transferFromBtoA.getItems().getFirst().getAllocatedQuantity());
    }

    @Test
    @DisplayName("Test 16: Concurrency safety: Concurrent cross-facility picking requests cannot bypass authorization")
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
                    authService.assertCanPickTransfer(actor, transferFromAtoB);
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
    @DisplayName("Test 17: Already picked/packed transfer denied to unauthorized actor (authorization precedes state machine)")
    void alreadyPickedTransferDeniedToUnauthorizedActor() {
        StockTransfer pickedTransfer = createTransfer("TRF-2026-000305", warehouseA, warehouseB, centralManagerA, TransferStatus.PICKED);
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));
        when(transferRepo.lockById(pickedTransfer.getId())).thenReturn(Optional.of(pickedTransfer));

        PickRequest pickReq = new PickRequest(List.of(new PickRequest.Item(batchA.getId(), 50)));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.pick(centralManagerB.getId(), pickedTransfer.getId(), pickReq)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 18: Direct HTTP API attack replay: Central Warehouse Manager requesting foreign pick is denied with 403")
    void directApiAttackReplay_returns403() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        PickRequest attackPayload = new PickRequest(List.of(new PickRequest.Item(batchB.getId(), 50)));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.pick(centralManagerA.getId(), transferFromBtoA.getId(), attackPayload)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 19: Authorized picking successfully updates transfer status to PICKED and sets pickedQuantity")
    void authorizedPickingSuccessfullyUpdatesTransfer() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromAtoB.getId())).thenReturn(Optional.of(transferFromAtoB));

        PickRequest pickReq = new PickRequest(List.of(new PickRequest.Item(batchA.getId(), 50)));

        TransferResponse response = transferService.pick(centralManagerA.getId(), transferFromAtoB.getId(), pickReq);

        assertNotNull(response);
        assertEquals(TransferStatus.PICKED, transferFromAtoB.getStatus());
        assertEquals(50, transferFromAtoB.getItems().getFirst().getPickedQuantity());
    }

    @Test
    @DisplayName("Test 20: Incomplete pick or invalid pick quantity validation occurs only after authorization passes")
    void invalidPickQuantityHandledAfterAuthorization() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromAtoB.getId())).thenReturn(Optional.of(transferFromAtoB));

        // Attempting pick with quantity 30 instead of allocated 50
        PickRequest invalidQuantityReq = new PickRequest(List.of(new PickRequest.Item(batchA.getId(), 30)));

        DomainException dex = assertThrows(
                DomainException.class,
                () -> transferService.pick(centralManagerA.getId(), transferFromAtoB.getId(), invalidQuantityReq)
        );
        assertEquals("INVALID_PICK_QUANTITY", dex.getCode());
    }

    @Test
    @DisplayName("Test 21: Unallocated batch in pick request throws UNALLOCATED_BATCH only after authorization passes")
    void unallocatedBatchHandledAfterAuthorization() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromAtoB.getId())).thenReturn(Optional.of(transferFromAtoB));

        UUID nonAllocatedBatchId = UUID.randomUUID();
        PickRequest invalidBatchReq = new PickRequest(List.of(new PickRequest.Item(nonAllocatedBatchId, 50)));

        DomainException dex = assertThrows(
                DomainException.class,
                () -> transferService.pick(centralManagerA.getId(), transferFromAtoB.getId(), invalidBatchReq)
        );
        assertEquals("UNALLOCATED_BATCH", dex.getCode());
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
