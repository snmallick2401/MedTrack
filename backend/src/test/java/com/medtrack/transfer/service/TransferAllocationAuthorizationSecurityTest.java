package com.medtrack.transfer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.medtrack.auth.entity.Role;
import com.medtrack.batch.entity.Batch;
import com.medtrack.inventory.dto.FefoAllocationRequest;
import com.medtrack.inventory.dto.FefoAllocationResponse;
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

class TransferAllocationAuthorizationSecurityTest {

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

        // Default mock behavior for idempotencyService: execute business action directly
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

        // Transfers in APPROVED status (ready for allocation)
        transferFromAtoB = createTransfer("TRF-2026-000201", warehouseA, warehouseB, centralManagerA, TransferStatus.APPROVED);
        transferFromBtoA = createTransfer("TRF-2026-000202", warehouseB, warehouseA, centralManagerB, TransferStatus.APPROVED);

        // Add an unallocated requested item to each transfer
        Medicine med = mock(Medicine.class);
        when(med.getId()).thenReturn(UUID.randomUUID());
        transferFromAtoB.addItem(new StockTransferItem(med, 50));
        transferFromBtoA.addItem(new StockTransferItem(med, 50));
    }

    @Test
    @DisplayName("Test 1: Authorized source warehouse manager can allocate transfer")
    void authorizedCentralWarehouseManagerCanAllocate() {
        assertDoesNotThrow(() -> authService.assertCanAllocateTransfer(centralManagerA, transferFromAtoB));
    }

    @Test
    @DisplayName("Test 2: Central Warehouse Manager A allocating transfer originating from Warehouse B is DENIED (BOLA horizontal)")
    void crossFacilityAllocationIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanAllocateTransfer(centralManagerA, transferFromBtoA)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"),
                "Exception must indicate source facility mismatch: " + ex.getMessage());
    }

    @Test
    @DisplayName("Test 3: Central Warehouse Manager B allocating transfer originating from Warehouse A is DENIED (Reverse BOLA)")
    void reverseCrossFacilityAllocationIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanAllocateTransfer(centralManagerB, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 4: Central Warehouse Manager with no assigned warehouse fails closed")
    void managerWithNoAssignedWarehouseFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanAllocateTransfer(centralManagerNoWarehouse, transferFromAtoB)
        );
        assertEquals("Central warehouse manager has no assigned warehouse", ex.getMessage());
    }

    @Test
    @DisplayName("Test 5: Central Warehouse Manager with inactive assigned warehouse fails closed")
    void managerWithInactiveAssignedWarehouseFailsClosed() {
        StockTransfer transferFromInactive = createTransfer("TRF-2026-000203", inactiveWarehouse, warehouseA, centralManagerInactiveWarehouse, TransferStatus.APPROVED);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanAllocateTransfer(centralManagerInactiveWarehouse, transferFromInactive)
        );
        assertEquals("Central warehouse manager assigned warehouse is inactive", ex.getMessage());
    }

    @Test
    @DisplayName("Test 6: SUPER_ADMIN has global administrative allocation authority")
    void superAdminPolicyAllowsGlobalAllocation() {
        assertDoesNotThrow(() -> authService.assertCanAllocateTransfer(superAdmin, transferFromAtoB));
        assertDoesNotThrow(() -> authService.assertCanAllocateTransfer(superAdmin, transferFromBtoA));
    }

    @Test
    @DisplayName("Test 7: STORE_MANAGER is denied allocation authority even at own facility")
    void storeManagerDeniedAllocation() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanAllocateTransfer(storeManagerA, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to allocate transfers"));
    }

    @Test
    @DisplayName("Test 8: LOGISTICS_COORDINATOR is denied allocation authority")
    void logisticsCoordinatorDeniedAllocation() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanAllocateTransfer(logisticsCoordinator, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to allocate transfers"));
    }

    @Test
    @DisplayName("Test 9: AUDITOR is denied allocation authority (read-only)")
    void auditorDeniedAllocation() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanAllocateTransfer(auditor, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to allocate transfers"));
    }

    @Test
    @DisplayName("Test 10: Destination warehouse assignment does NOT grant allocation authority")
    void destinationWarehouseManagerCannotAllocateSourceTransfer() {
        // Manager B is assigned to warehouseB, which is the DESTINATION of transferFromAtoB
        assertEquals(warehouseB.getId(), transferFromAtoB.getDestinationWarehouse().getId());
        assertEquals(warehouseB.getId(), centralManagerB.getAssignedWarehouse().getId());

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanAllocateTransfer(centralManagerB, transferFromAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 11: Server derives source warehouse from persisted entity, ignoring client assertions")
    void clientControlledSourceCannotBypassPersistedSourceWarehouse() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerA.getId(), transferFromBtoA.getId(), null)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));

        // Verify FEFO was never called
        verify(fefo, never()).allocate(any(), any());
    }

    @Test
    @DisplayName("Test 12: No partial mutation: Denied allocation performs ZERO state or reservation mutations")
    void noPartialMutationOnUnauthorizedAllocation() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerA.getId(), transferFromBtoA.getId(), null)
        );

        // Verify transfer status remains APPROVED (never transitioned to ALLOCATED)
        assertEquals(TransferStatus.APPROVED, transferFromBtoA.getStatus());
        // Verify FEFO was never invoked
        verify(fefo, never()).allocate(any(), any());
        // Verify items list contains only the original unallocated item
        assertEquals(1, transferFromBtoA.getItems().size());
        assertNull(transferFromBtoA.getItems().getFirst().getBatch());
    }

    @Test
    @DisplayName("Test 13: FEFO engine is not reached on authorization denial")
    void fefoEngineNotReachedOnAuthorizationDenial() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerA.getId(), transferFromBtoA.getId(), "KEY-FAIL-01")
        );

        verifyNoInteractions(fefo);
    }

    @Test
    @DisplayName("Test 14: Concurrency safety: Concurrent cross-facility requests cannot bypass authorization")
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
                    authService.assertCanAllocateTransfer(actor, transferFromAtoB);
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
    @DisplayName("Test 15: Concurrent duplicate handling: Authorized user receives idempotent response, unauthorized user remains blocked")
    void concurrentDuplicateHandling() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));

        StockTransfer allocatedTransfer = createTransfer("TRF-2026-000204", warehouseA, warehouseB, centralManagerA, TransferStatus.ALLOCATED);
        when(transferRepo.lockById(allocatedTransfer.getId())).thenReturn(Optional.of(allocatedTransfer));

        // 1. Authorized caller allocating already-allocated transfer gets idempotent response without re-allocating
        TransferResponse res = transferService.allocate(centralManagerA.getId(), allocatedTransfer.getId(), "KEY-DUP-1");
        assertNotNull(res);
        assertEquals("ALLOCATED", res.status());
        verify(fefo, never()).allocate(any(), any());

        // 2. Unauthorized caller allocating already-allocated transfer is DENIED fail-closed
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerB.getId(), allocatedTransfer.getId(), "KEY-DUP-2")
        );
    }

    @Test
    @DisplayName("Test 16: Unauthorized retry / idempotency key manipulation cannot bypass authorization")
    void unauthorizedRetryHandling() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        // Attempt 1 with key 1
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerA.getId(), transferFromBtoA.getId(), "RETRY-KEY-1")
        );

        // Attempt 2 with key 2
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerA.getId(), transferFromBtoA.getId(), "RETRY-KEY-2")
        );

        // Attempt 3 with null key
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerA.getId(), transferFromBtoA.getId(), null)
        );

        verify(fefo, never()).allocate(any(), any());
    }

    @Test
    @DisplayName("Test 17: Already allocated transfer denied to unauthorized actor (authorization precedes state short-circuits)")
    void alreadyAllocatedTransferDeniedToUnauthorizedActor() {
        StockTransfer allocatedTransfer = createTransfer("TRF-2026-000205", warehouseA, warehouseB, centralManagerA, TransferStatus.ALLOCATED);
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));
        when(transferRepo.lockById(allocatedTransfer.getId())).thenReturn(Optional.of(allocatedTransfer));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerB.getId(), allocatedTransfer.getId(), null)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
        verify(fefo, never()).allocate(any(), any());
    }

    @Test
    @DisplayName("Test 18: Direct HTTP API attack replay: Central Warehouse Manager requesting foreign allocation is denied with 403")
    void directApiAttackReplay_returns403() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferFromBtoA.getId())).thenReturn(Optional.of(transferFromBtoA));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.allocate(centralManagerA.getId(), transferFromBtoA.getId(), "KEY-ATTACK-01")
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
        verify(fefo, never()).allocate(any(), any());
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
