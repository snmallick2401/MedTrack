package com.medtrack.shipment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.medtrack.auth.entity.Role;
import com.medtrack.inventory.service.InventoryMovementService;
import com.medtrack.shared.idempotency.IdempotencyService;
import com.medtrack.shipment.dto.ReceiveRequest;
import com.medtrack.shipment.entity.Shipment;
import com.medtrack.shipment.entity.ShipmentStatus;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.transfer.entity.StockTransfer;
import com.medtrack.transfer.entity.TransferStatus;
import com.medtrack.transfer.repository.StockTransferRepository;
import com.medtrack.user.entity.User;
import com.medtrack.user.entity.UserStatus;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.entity.StorageLocation;
import com.medtrack.warehouse.repository.StorageLocationRepository;
import com.medtrack.warehouse.entity.Warehouse;
import com.medtrack.warehouse.entity.WarehouseStatus;
import com.medtrack.warehouse.entity.WarehouseType;
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

class ShipmentAuthorizationSecurityTest {

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

    private User storeManagerA;
    private User storeManagerB;
    private User storeManagerNoWarehouse;
    private User storeManagerInactiveWarehouse;
    private User superAdmin;
    private User centralWarehouseManager;
    private User logisticsCoordinator;
    private User auditor;

    private StockTransfer transferToA;
    private StockTransfer transferToB;

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
        warehouseA = createWarehouse("DS01", "Distribution Store North", WarehouseStatus.ACTIVE);
        warehouseB = createWarehouse("DS02", "Distribution Store South", WarehouseStatus.ACTIVE);
        inactiveWarehouse = createWarehouse("DS03", "Decommissioned Store", WarehouseStatus.INACTIVE);

        // Roles
        Role roleStoreManager = createRole("STORE_MANAGER");
        Role roleSuperAdmin = createRole("SUPER_ADMIN");
        Role roleCentralManager = createRole("CENTRAL_WAREHOUSE_MANAGER");
        Role roleLogistics = createRole("LOGISTICS_COORDINATOR");
        Role roleAuditor = createRole("AUDITOR");

        // Users
        storeManagerA = createUser("mgr.a@medtrack.local", roleStoreManager, warehouseA);
        storeManagerB = createUser("mgr.b@medtrack.local", roleStoreManager, warehouseB);
        storeManagerNoWarehouse = createUser("mgr.nowh@medtrack.local", roleStoreManager, null);
        storeManagerInactiveWarehouse = createUser("mgr.inact@medtrack.local", roleStoreManager, inactiveWarehouse);
        superAdmin = createUser("admin@medtrack.local", roleSuperAdmin, null);
        centralWarehouseManager = createUser("cwm@medtrack.local", roleCentralManager, warehouseA);
        logisticsCoordinator = createUser("logistics@medtrack.local", roleLogistics, null);
        auditor = createUser("auditor@medtrack.local", roleAuditor, null);

        // Transfers
        transferToA = createTransfer("TRF-2026-000001", warehouseB, warehouseA, storeManagerA);
        transferToB = createTransfer("TRF-2026-000002", warehouseA, warehouseB, storeManagerB);
    }

    @Test
    @DisplayName("Test 1: Store Manager A receiving transfer destined for Warehouse A is authorized")
    void authorizedStoreManagerCanReceive() {
        assertDoesNotThrow(() -> authService.assertCanReceiveTransfer(storeManagerA, transferToA));
    }

    @Test
    @DisplayName("Test 2: Store Manager A receiving transfer destined for Warehouse B is DENIED (BOLA horizontal)")
    void crossFacilityReceivingIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanReceiveTransfer(storeManagerA, transferToB)
        );
        assertTrue(ex.getMessage().contains("not assigned to destination warehouse"),
                "Exception message must clarify facility mismatch: " + ex.getMessage());
    }

    @Test
    @DisplayName("Test 3: Store Manager B receiving transfer destined for Warehouse A is DENIED (Reverse BOLA)")
    void reverseCrossFacilityReceivingIsDenied() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanReceiveTransfer(storeManagerB, transferToA)
        );
        assertTrue(ex.getMessage().contains("not assigned to destination warehouse"));
    }

    @Test
    @DisplayName("Test 4: Caller cannot override destination facility via request body")
    void clientSuppliedStorageLocationCannotBypassFacilityScope() {
        // Attacker storeManagerA calls receive on transferToB, but tries to pass a storageLocation belonging to Warehouse A
        when(userRepo.findById(storeManagerA.getId())).thenReturn(Optional.of(storeManagerA));
        when(transferRepo.lockById(transferToB.getId())).thenReturn(Optional.of(transferToB));

        ReceiveRequest requestWithWarehouseALocation = new ReceiveRequest(
                UUID.randomUUID(),
                List.of(new ReceiveRequest.Item(UUID.randomUUID(), 10, 0)),
                null
        );

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> shipmentService.receive(storeManagerA.getId(), transferToB.getId(), requestWithWarehouseALocation, null)
        );

        assertTrue(ex.getMessage().contains("not assigned to destination warehouse"));
        // Verify storage location lookup was never even attempted due to early fail-closed authorization
        verify(locationRepo, never()).findById(any());
        verify(inventoryService, never()).receive(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Test 5: Store Manager with no assigned warehouse fails closed")
    void missingWarehouseAssignmentFailsClosed() {
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanReceiveTransfer(storeManagerNoWarehouse, transferToA)
        );
        assertEquals("Store manager has no assigned warehouse", ex.getMessage());
    }

    @Test
    @DisplayName("Test 6: Store Manager with inactive assigned warehouse fails closed")
    void inactiveWarehouseAssignmentFailsClosed() {
        StockTransfer transferToInactive = createTransfer("TRF-2026-000003", warehouseA, inactiveWarehouse, storeManagerInactiveWarehouse);
        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanReceiveTransfer(storeManagerInactiveWarehouse, transferToInactive)
        );
        assertEquals("Store manager assigned warehouse is inactive", ex.getMessage());
    }

    @Test
    @DisplayName("Test 7: Vertical privilege escalation: Unauthorized roles cannot receive transfers")
    void unauthorizedRolesAreDenied() {
        AccessDeniedException ex1 = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanReceiveTransfer(centralWarehouseManager, transferToA)
        );
        assertTrue(ex1.getMessage().contains("not authorized to receive transfers"));

        AccessDeniedException ex2 = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanReceiveTransfer(logisticsCoordinator, transferToA)
        );
        assertTrue(ex2.getMessage().contains("not authorized to receive transfers"));

        AccessDeniedException ex3 = assertThrows(
                AccessDeniedException.class,
                () -> authService.assertCanReceiveTransfer(auditor, transferToA)
        );
        assertTrue(ex3.getMessage().contains("not authorized to receive transfers"));
    }

    @Test
    @DisplayName("Test 8: SUPER_ADMIN can receive transfer across any facility")
    void superAdminPolicyAllowsGlobalReceiving() {
        assertDoesNotThrow(() -> authService.assertCanReceiveTransfer(superAdmin, transferToA));
        assertDoesNotThrow(() -> authService.assertCanReceiveTransfer(superAdmin, transferToB));
    }

    @Test
    @DisplayName("Test 9: No partial commit: Unauthorized receipt performs ZERO mutations")
    void noPartialCommitOnAuthorizationFailure() {
        when(userRepo.findById(storeManagerA.getId())).thenReturn(Optional.of(storeManagerA));
        when(transferRepo.lockById(transferToB.getId())).thenReturn(Optional.of(transferToB));

        // Create mock shipment for transferToB in DISPATCHED status
        Shipment shipmentB = mock(Shipment.class);
        when(shipmentB.getStatus()).thenReturn(ShipmentStatus.DISPATCHED);
        when(shipmentRepo.lockByTransferId(transferToB.getId())).thenReturn(Optional.of(shipmentB));

        ReceiveRequest request = new ReceiveRequest(UUID.randomUUID(), List.of(), null);

        assertThrows(
                AccessDeniedException.class,
                () -> shipmentService.receive(storeManagerA.getId(), transferToB.getId(), request, null)
        );

        // Verify zero state changes
        assertEquals(TransferStatus.DISPATCHED, transferToB.getStatus(), "Transfer status must not mutate");
        verify(shipmentB, never()).deliver();
        verify(inventoryService, never()).receive(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Test 10: Concurrency safety: Concurrent cross-facility requests cannot bypass authorization")
    void concurrencySafetyUnderLoad() throws InterruptedException {
        when(userRepo.findById(storeManagerA.getId())).thenReturn(Optional.of(storeManagerA));
        when(userRepo.findById(storeManagerB.getId())).thenReturn(Optional.of(storeManagerB));
        when(transferRepo.lockById(transferToA.getId())).thenReturn(Optional.of(transferToA));

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
                    User actor = isUnauthorized ? storeManagerB : storeManagerA;
                    authService.assertCanReceiveTransfer(actor, transferToA);
                    authorizedCount.incrementAndGet();
                } catch (AccessDeniedException e) {
                    deniedCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Unexpected exception: " + e.getMessage());
                }
            }));
        }

        latch.countDown(); // Start all threads simultaneously
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(10, deniedCount.get(), "Exactly 10 unauthorized requests must be rejected");
        assertEquals(10, authorizedCount.get(), "Exactly 10 authorized requests must pass authorization");
    }

    // --- Helper Methods ---

    private Warehouse createWarehouse(String code, String name, WarehouseStatus status) {
        Warehouse w = new Warehouse(code, name, WarehouseType.DISTRIBUTION_STORE, "Address " + code,
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

    private StockTransfer createTransfer(String number, Warehouse source, Warehouse dest, User requester) {
        StockTransfer t = new StockTransfer(number, source, dest, requester, "Transfer notes");
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(t, "status", TransferStatus.DISPATCHED);
        return t;
    }
}
