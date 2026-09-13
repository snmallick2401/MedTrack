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
import com.medtrack.shipment.entity.Shipment;
import com.medtrack.shipment.entity.ShipmentStatus;
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

class TransferApprovalCancellationAuthorizationSecurityTest {

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
    private Warehouse storeWarehouseA;
    private Warehouse storeWarehouseB;
    private Warehouse inactiveWarehouse;

    private User centralManagerA;
    private User centralManagerB;
    private User centralManagerNoWarehouse;
    private User centralManagerInactiveWarehouse;
    private User storeManagerA;
    private User storeManagerB;
    private User superAdmin;
    private User logisticsCoordinator;
    private User auditor;

    private StockTransfer transferRequestedAtoB;
    private StockTransfer transferRequestedBtoA;
    private StockTransfer transferAllocatedAtoStore;
    private StockTransfer transferAllocatedBtoStore;

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

        // Mock idempotency to execute business lambda directly
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
        storeWarehouseA = createWarehouse("ST01", "Store Pharmacy Alpha", WarehouseStatus.ACTIVE);
        storeWarehouseB = createWarehouse("ST02", "Store Pharmacy Beta", WarehouseStatus.ACTIVE);
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
        storeManagerA = createUser("store.a@medtrack.local", roleStoreManager, storeWarehouseA);
        storeManagerB = createUser("store.b@medtrack.local", roleStoreManager, storeWarehouseB);
        superAdmin = createUser("admin@medtrack.local", roleSuperAdmin, null);
        logisticsCoordinator = createUser("logistics@medtrack.local", roleLogistics, null);
        auditor = createUser("auditor@medtrack.local", roleAuditor, null);

        // Transfers in REQUESTED status (for approval tests)
        transferRequestedAtoB = createTransfer("TRF-2026-000501", warehouseA, warehouseB, storeManagerA, TransferStatus.REQUESTED);
        transferRequestedBtoA = createTransfer("TRF-2026-000502", warehouseB, warehouseA, storeManagerB, TransferStatus.REQUESTED);

        // Transfers in ALLOCATED status (for cancellation tests)
        transferAllocatedAtoStore = createTransfer("TRF-2026-000503", warehouseA, storeWarehouseA, storeManagerA, TransferStatus.ALLOCATED);
        transferAllocatedBtoStore = createTransfer("TRF-2026-000504", warehouseB, storeWarehouseB, storeManagerB, TransferStatus.ALLOCATED);

        Medicine med = mock(Medicine.class);
        when(med.getId()).thenReturn(UUID.randomUUID());
        Batch batch = mock(Batch.class);
        when(batch.getId()).thenReturn(UUID.randomUUID());

        StockTransferItem item = new StockTransferItem(med, batch, 100);
        transferAllocatedAtoStore.addItem(item);
    }

    // =========================================================================
    // PART 1: APPROVAL AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 1: Authorized source warehouse manager can approve transfer")
    void authorizedSourceWarehouseManagerCanApprove() {
        assertDoesNotThrow(() -> authService.assertCanApproveTransfer(centralManagerA, transferRequestedAtoB));
    }

    @Test
    @DisplayName("Test 2: Central Warehouse Manager A approving transfer originating from Warehouse B is DENIED (BOLA horizontal)")
    void crossFacilityApprovalIsDenied_HorizontalBOLA() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(centralManagerA, transferRequestedBtoA)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 3: Central Warehouse Manager B approving transfer originating from Warehouse A is DENIED (Reverse BOLA)")
    void reverseCrossFacilityApprovalIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(centralManagerB, transferRequestedAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 4: Destination warehouse manager cannot approve source transfer")
    void destinationWarehouseManagerCannotApproveSourceTransfer() {
        // Manager B is assigned to warehouseB, which is the destination of transferRequestedAtoB
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(centralManagerB, transferRequestedAtoB)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 5: Central Warehouse Manager with no assigned warehouse fails closed on approval")
    void managerWithNoAssignedWarehouseFailsClosed_Approve() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(centralManagerNoWarehouse, transferRequestedAtoB)
        );
        assertEquals("Central warehouse manager has no assigned warehouse", ex.getMessage());
    }

    @Test
    @DisplayName("Test 6: Central Warehouse Manager with inactive assigned warehouse fails closed on approval")
    void managerWithInactiveAssignedWarehouseFailsClosed_Approve() {
        StockTransfer transferInactive = createTransfer("TRF-2026-000505", inactiveWarehouse, warehouseB, storeManagerA, TransferStatus.REQUESTED);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(centralManagerInactiveWarehouse, transferInactive)
        );
        assertEquals("Central warehouse manager assigned warehouse is inactive", ex.getMessage());
    }

    @Test
    @DisplayName("Test 7: Missing actor principal fails closed on approval")
    void missingActorFailsClosed_Approve() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(null, transferRequestedAtoB)
        );
        assertEquals("Authenticated actor is required", ex.getMessage());
    }

    @Test
    @DisplayName("Test 8: Missing stock transfer fails closed on approval")
    void missingTransferFailsClosed_Approve() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(centralManagerA, null)
        );
        assertEquals("Stock transfer is required", ex.getMessage());
    }

    @Test
    @DisplayName("Test 9: SUPER_ADMIN has global administrative approval authority")
    void superAdminPolicyAllowsGlobalApproval() {
        assertDoesNotThrow(() -> authService.assertCanApproveTransfer(superAdmin, transferRequestedAtoB));
        assertDoesNotThrow(() -> authService.assertCanApproveTransfer(superAdmin, transferRequestedBtoA));
    }

    @Test
    @DisplayName("Test 10: STORE_MANAGER is denied approval authority")
    void storeManagerDeniedApproval() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(storeManagerA, transferRequestedAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to approve transfers"));
    }

    @Test
    @DisplayName("Test 11: LOGISTICS_COORDINATOR is denied approval authority")
    void logisticsCoordinatorDeniedApproval() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(logisticsCoordinator, transferRequestedAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to approve transfers"));
    }

    @Test
    @DisplayName("Test 12: AUDITOR is denied approval authority (read-only)")
    void auditorDeniedApproval() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanApproveTransfer(auditor, transferRequestedAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to approve transfers"));
    }

    @Test
    @DisplayName("Test 13: No partial mutation: Denied approval leaves status REQUESTED and approvedBy null")
    void noPartialMutationOnUnauthorizedApproval() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferRequestedBtoA.getId())).thenReturn(Optional.of(transferRequestedBtoA));

        assertThrows(
                AccessDeniedException.class,
                () -> transferService.approve(centralManagerA.getId(), transferRequestedBtoA.getId())
        );

        assertEquals(TransferStatus.REQUESTED, transferRequestedBtoA.getStatus());
    }

    @Test
    @DisplayName("Test 14: Already approved transfer denied to unauthorized actor (authorization precedes state check)")
    void alreadyApprovedTransferDeniedToUnauthorizedActor() {
        StockTransfer approvedTransfer = createTransfer("TRF-2026-000506", warehouseA, warehouseB, storeManagerA, TransferStatus.APPROVED);
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));
        when(transferRepo.lockById(approvedTransfer.getId())).thenReturn(Optional.of(approvedTransfer));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.approve(centralManagerB.getId(), approvedTransfer.getId(), null)
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
    }

    @Test
    @DisplayName("Test 15: Concurrent duplicate approval handling with idempotency")
    void concurrentDuplicateHandling_ApproveIdempotency() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));

        StockTransfer approvedTransfer = createTransfer("TRF-2026-000507", warehouseA, warehouseB, storeManagerA, TransferStatus.APPROVED);
        when(transferRepo.lockById(approvedTransfer.getId())).thenReturn(Optional.of(approvedTransfer));

        // 1. Authorized caller receives idempotent response without error
        TransferResponse res = transferService.approve(centralManagerA.getId(), approvedTransfer.getId(), "KEY-APP-DUP-1");
        assertNotNull(res);
        assertEquals("APPROVED", res.status());

        // 2. Unauthorized caller is denied fail-closed
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.approve(centralManagerB.getId(), approvedTransfer.getId(), "KEY-APP-DUP-2")
        );
    }

    @Test
    @DisplayName("Test 16: Concurrency safety under load for approval requests")
    void concurrencySafetyUnderLoad_Approve() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger deniedCount = new AtomicInteger(0);
        AtomicInteger authorizedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final boolean isUnauthorized = (i % 2 == 0);
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    User actor = isUnauthorized ? centralManagerB : centralManagerA;
                    authService.assertCanApproveTransfer(actor, transferRequestedAtoB);
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

        assertEquals(10, deniedCount.get());
        assertEquals(10, authorizedCount.get());
    }

    // =========================================================================
    // PART 2: CANCELLATION AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 17: Authorized source warehouse manager can cancel transfer")
    void authorizedSourceWarehouseManagerCanCancel() {
        assertDoesNotThrow(() -> authService.assertCanCancelTransfer(centralManagerA, transferAllocatedAtoStore));
    }

    @Test
    @DisplayName("Test 18: Original requester store manager can cancel their own transfer")
    void authorizedRequesterStoreManagerCanCancelOwnTransfer() {
        // storeManagerA is transferAllocatedAtoStore.requestedBy
        assertEquals(storeManagerA.getId(), transferAllocatedAtoStore.getRequestedBy().getId());
        assertDoesNotThrow(() -> authService.assertCanCancelTransfer(storeManagerA, transferAllocatedAtoStore));
    }

    @Test
    @DisplayName("Test 19: Central Warehouse Manager A cancelling transfer originating from Warehouse B is DENIED (BOLA horizontal)")
    void crossFacilityCancellationIsDenied_HorizontalBOLA() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(centralManagerA, transferAllocatedBtoStore)
        );
        assertTrue(ex.getMessage().contains("not authorized to cancel foreign transfer"));
    }

    @Test
    @DisplayName("Test 20: Central Warehouse Manager B cancelling transfer originating from Warehouse A is DENIED (Reverse BOLA)")
    void reverseCrossFacilityCancellationIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(centralManagerB, transferAllocatedAtoStore)
        );
        assertTrue(ex.getMessage().contains("not authorized to cancel foreign transfer"));
    }

    @Test
    @DisplayName("Test 21: Non-requester store manager B is DENIED cancellation of transfer requested by store manager A")
    void nonRequesterStoreManagerDeniedCancellation() {
        assertNotEquals(storeManagerB.getId(), transferAllocatedAtoStore.getRequestedBy().getId());
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(storeManagerB, transferAllocatedAtoStore)
        );
        assertTrue(ex.getMessage().contains("not authorized to cancel transfer"));
    }

    @Test
    @DisplayName("Test 22: Destination warehouse manager cannot cancel transfer unless they are the requester")
    void destinationWarehouseManagerCannotCancelSourceTransferUnlessRequester() {
        // transferRequestedAtoB: source = warehouseA, destination = warehouseB, requestedBy = storeManagerA
        assertNotEquals(centralManagerB.getId(), transferRequestedAtoB.getRequestedBy().getId());
        assertEquals(warehouseB.getId(), centralManagerB.getAssignedWarehouse().getId());

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(centralManagerB, transferRequestedAtoB)
        );
        assertTrue(ex.getMessage().contains("not authorized to cancel foreign transfer"));
    }

    @Test
    @DisplayName("Test 23: User with no assigned warehouse fails closed on cancellation")
    void managerWithNoAssignedWarehouseFailsClosed_Cancel() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(centralManagerNoWarehouse, transferAllocatedAtoStore)
        );
        assertEquals("User has no assigned warehouse", ex.getMessage());
    }

    @Test
    @DisplayName("Test 24: User with inactive assigned warehouse fails closed on cancellation")
    void managerWithInactiveAssignedWarehouseFailsClosed_Cancel() {
        StockTransfer transferInactive = createTransfer("TRF-2026-000508", inactiveWarehouse, storeWarehouseA, centralManagerInactiveWarehouse, TransferStatus.ALLOCATED);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(centralManagerInactiveWarehouse, transferInactive)
        );
        assertEquals("User assigned warehouse is inactive", ex.getMessage());
    }

    @Test
    @DisplayName("Test 25: Missing actor principal fails closed on cancellation")
    void missingActorFailsClosed_Cancel() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(null, transferAllocatedAtoStore)
        );
        assertEquals("Authenticated actor is required", ex.getMessage());
    }

    @Test
    @DisplayName("Test 26: Missing stock transfer fails closed on cancellation")
    void missingTransferFailsClosed_Cancel() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(centralManagerA, null)
        );
        assertEquals("Stock transfer is required", ex.getMessage());
    }

    @Test
    @DisplayName("Test 27: SUPER_ADMIN has global administrative cancellation authority")
    void superAdminPolicyAllowsGlobalCancellation() {
        assertDoesNotThrow(() -> authService.assertCanCancelTransfer(superAdmin, transferAllocatedAtoStore));
        assertDoesNotThrow(() -> authService.assertCanCancelTransfer(superAdmin, transferAllocatedBtoStore));
    }

    @Test
    @DisplayName("Test 28: LOGISTICS_COORDINATOR is denied cancellation authority")
    void logisticsCoordinatorDeniedCancellation() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(logisticsCoordinator, transferAllocatedAtoStore)
        );
        assertTrue(ex.getMessage().contains("not authorized to cancel transfer"));
    }

    @Test
    @DisplayName("Test 29: AUDITOR is denied cancellation authority (read-only)")
    void auditorDeniedCancellation() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanCancelTransfer(auditor, transferAllocatedAtoStore)
        );
        assertTrue(ex.getMessage().contains("not authorized to cancel transfer"));
    }

    @Test
    @DisplayName("Test 30: No partial mutation on denied cancellation: ZERO reservations released and status unchanged")
    void noPartialMutationOnUnauthorizedCancellation() {
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));
        when(transferRepo.lockById(transferAllocatedAtoStore.getId())).thenReturn(Optional.of(transferAllocatedAtoStore));

        assertThrows(
                AccessDeniedException.class,
                () -> transferService.cancel(centralManagerB.getId(), transferAllocatedAtoStore.getId(), "Malicious cancel", null)
        );

        // Verify transfer status remains ALLOCATED
        assertEquals(TransferStatus.ALLOCATED, transferAllocatedAtoStore.getStatus());
        // Verify inventory reservations were NEVER released
        verify(inventory, never()).releaseReservations(any(), any(), any(), any());
        // Verify shipments were never cancelled
        verify(shipmentRepo, never()).findByTransfer_Id(any());
    }

    @Test
    @DisplayName("Test 31: Already cancelled transfer denied to unauthorized actor (authorization precedes terminal check)")
    void alreadyCancelledTransferDeniedToUnauthorizedActor() {
        StockTransfer cancelledTransfer = createTransfer("TRF-2026-000509", warehouseA, storeWarehouseA, storeManagerA, TransferStatus.CANCELLED);
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));
        when(transferRepo.lockById(cancelledTransfer.getId())).thenReturn(Optional.of(cancelledTransfer));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.cancel(centralManagerB.getId(), cancelledTransfer.getId(), "Cancel retry", null)
        );
        assertTrue(ex.getMessage().contains("not authorized to cancel foreign transfer"));
        verify(inventory, never()).releaseReservations(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Test 32: Concurrency safety under load for cancellation requests")
    void concurrencySafetyUnderLoad_Cancel() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger deniedCount = new AtomicInteger(0);
        AtomicInteger authorizedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final boolean isUnauthorized = (i % 2 == 0);
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    User actor = isUnauthorized ? centralManagerB : centralManagerA;
                    authService.assertCanCancelTransfer(actor, transferAllocatedAtoStore);
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

        assertEquals(10, deniedCount.get());
        assertEquals(10, authorizedCount.get());
    }

    @Test
    @DisplayName("Test 33: Direct API attack replay: Unauthorized approve returns 403")
    void directApiAttackReplay_Approve_returns403() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferRequestedBtoA.getId())).thenReturn(Optional.of(transferRequestedBtoA));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.approve(centralManagerA.getId(), transferRequestedBtoA.getId(), "KEY-ATTACK-APP-01")
        );
        assertTrue(ex.getMessage().contains("not assigned to source warehouse"));
        assertEquals(TransferStatus.REQUESTED, transferRequestedBtoA.getStatus());
    }

    @Test
    @DisplayName("Test 34: Direct API attack replay: Unauthorized cancel returns 403")
    void directApiAttackReplay_Cancel_returns403() {
        when(userRepo.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(transferRepo.lockById(transferAllocatedBtoStore.getId())).thenReturn(Optional.of(transferAllocatedBtoStore));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.cancel(centralManagerA.getId(), transferAllocatedBtoStore.getId(), "Cancel reason", "KEY-ATTACK-CNC-01")
        );
        assertTrue(ex.getMessage().contains("not authorized to cancel foreign transfer"));
        assertEquals(TransferStatus.ALLOCATED, transferAllocatedBtoStore.getStatus());
        verify(inventory, never()).releaseReservations(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Test 35: Unauthorized cancellation retries with new idempotency keys remain denied")
    void unauthorizedRetryHandling_Cancel() {
        when(userRepo.findById(centralManagerB.getId())).thenReturn(Optional.of(centralManagerB));
        when(transferRepo.lockById(transferAllocatedAtoStore.getId())).thenReturn(Optional.of(transferAllocatedAtoStore));

        // Attempt 1 with key 1
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.cancel(centralManagerB.getId(), transferAllocatedAtoStore.getId(), "Reason 1", "KEY-RETRY-1")
        );

        // Attempt 2 with key 2
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.cancel(centralManagerB.getId(), transferAllocatedAtoStore.getId(), "Reason 2", "KEY-RETRY-2")
        );

        // Attempt 3 with null key
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.cancel(centralManagerB.getId(), transferAllocatedAtoStore.getId(), "Reason 3", null)
        );

        assertEquals(TransferStatus.ALLOCATED, transferAllocatedAtoStore.getStatus());
        verify(inventory, never()).releaseReservations(any(), any(), any(), any());
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
