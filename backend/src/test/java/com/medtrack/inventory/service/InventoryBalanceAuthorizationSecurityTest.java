package com.medtrack.inventory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.medtrack.auth.entity.Role;
import com.medtrack.batch.entity.Batch;
import com.medtrack.batch.entity.BatchStatus;
import com.medtrack.inventory.controller.InventoryController;
import com.medtrack.inventory.dto.InventoryBalanceResponse;
import com.medtrack.inventory.entity.InventoryBalance;
import com.medtrack.inventory.repository.InventoryBalanceRepository;
import com.medtrack.inventory.repository.InventoryJournalEntryRepository;
import com.medtrack.medicine.entity.Medicine;
import com.medtrack.supplier.entity.Supplier;
import com.medtrack.user.entity.User;
import com.medtrack.user.entity.UserStatus;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.entity.StorageLocation;
import com.medtrack.warehouse.entity.Warehouse;
import com.medtrack.warehouse.entity.WarehouseStatus;
import com.medtrack.warehouse.entity.WarehouseType;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryBalanceAuthorizationSecurityTest {

    private InventoryAuthorizationService authorizationService;
    private InventoryController controller;

    private InventoryBalanceRepository balanceRepository;
    private InventoryJournalEntryRepository journalRepository;
    private InboundService inboundService;
    private FefoAllocationEngine fefoEngine;
    private UserRepository userRepository;

    private Warehouse warehouseA;
    private Warehouse warehouseB;
    private Warehouse centralWarehouseA;
    private Warehouse inactiveWarehouse;

    private User storeManagerA;
    private User storeManagerB;
    private User centralManagerA;
    private User storeManagerNoWarehouse;
    private User storeManagerInactiveWarehouse;
    private User superAdmin;
    private User auditor;
    private User logisticsCoordinator;

    private InventoryBalance balanceA;
    private InventoryBalance balanceB;

    @BeforeEach
    void setUp() {
        authorizationService = new InventoryAuthorizationService();

        balanceRepository = mock(InventoryBalanceRepository.class);
        journalRepository = mock(InventoryJournalEntryRepository.class);
        inboundService = mock(InboundService.class);
        fefoEngine = mock(FefoAllocationEngine.class);
        userRepository = mock(UserRepository.class);

        controller = new InventoryController(
                inboundService, fefoEngine, balanceRepository, journalRepository, authorizationService, userRepository
        );

        // Warehouses
        warehouseA = createWarehouse("DS01", "Dispensary North", WarehouseType.DISTRIBUTION_STORE, WarehouseStatus.ACTIVE);
        warehouseB = createWarehouse("DS02", "Dispensary South", WarehouseType.DISTRIBUTION_STORE, WarehouseStatus.ACTIVE);
        centralWarehouseA = createWarehouse("CW01", "Central Wh 1", WarehouseType.CENTRAL_WAREHOUSE, WarehouseStatus.ACTIVE);
        inactiveWarehouse = createWarehouse("DS03", "Dispensary Inactive", WarehouseType.DISTRIBUTION_STORE, WarehouseStatus.INACTIVE);

        // Roles
        Role roleStoreManager = createRole("STORE_MANAGER");
        Role roleCentralManager = createRole("CENTRAL_WAREHOUSE_MANAGER");
        Role roleSuperAdmin = createRole("SUPER_ADMIN");
        Role roleAuditor = createRole("AUDITOR");
        Role roleLogistics = createRole("LOGISTICS_COORDINATOR");

        // Users
        storeManagerA = createUser("mgr.a@medtrack.local", roleStoreManager, warehouseA);
        storeManagerB = createUser("mgr.b@medtrack.local", roleStoreManager, warehouseB);
        centralManagerA = createUser("cw.a@medtrack.local", roleCentralManager, centralWarehouseA);
        storeManagerNoWarehouse = createUser("nowh@medtrack.local", roleStoreManager, null);
        storeManagerInactiveWarehouse = createUser("inact@medtrack.local", roleStoreManager, inactiveWarehouse);
        superAdmin = createUser("admin@medtrack.local", roleSuperAdmin, null);
        auditor = createUser("auditor@medtrack.local", roleAuditor, null);
        logisticsCoordinator = createUser("logistics@medtrack.local", roleLogistics, null);

        when(userRepository.findById(storeManagerA.getId())).thenReturn(Optional.of(storeManagerA));
        when(userRepository.findById(storeManagerB.getId())).thenReturn(Optional.of(storeManagerB));
        when(userRepository.findById(centralManagerA.getId())).thenReturn(Optional.of(centralManagerA));
        when(userRepository.findById(storeManagerNoWarehouse.getId())).thenReturn(Optional.of(storeManagerNoWarehouse));
        when(userRepository.findById(storeManagerInactiveWarehouse.getId())).thenReturn(Optional.of(storeManagerInactiveWarehouse));
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(auditor.getId())).thenReturn(Optional.of(auditor));
        when(userRepository.findById(logisticsCoordinator.getId())).thenReturn(Optional.of(logisticsCoordinator));

        // Sample Balances
        balanceA = createBalance(warehouseA, "BATCH-A001", 100);
        balanceB = createBalance(warehouseB, "BATCH-B001", 250);
    }

    @Test
    @DisplayName("Test 1: STORE_MANAGER A requesting Warehouse A -> ALLOW")
    void storeManagerA_canViewAssignedWarehouseBalances_success() {
        Pageable pageable = PageRequest.of(0, 20);
        when(balanceRepository.findByWarehouse_Id(eq(warehouseA.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(balanceA), pageable, 1));

        Page<InventoryBalanceResponse> result = controller.balances(
                storeManagerA.getId().toString(), warehouseA.getId(), 0, 20
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(warehouseA.getId(), result.getContent().getFirst().warehouseId());
        verify(balanceRepository, times(1)).findByWarehouse_Id(warehouseA.getId(), pageable);
    }

    @Test
    @DisplayName("Test 2: STORE_MANAGER A requesting Warehouse B -> DENY (403 AccessDeniedException)")
    void storeManagerA_requestingOtherWarehouseBalances_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.balances(storeManagerA.getId().toString(), warehouseB.getId(), 0, 20)
        );

        assertTrue(ex.getMessage().contains("not authorized"));
        verify(balanceRepository, never()).findByWarehouse_Id(any(), any());
        verify(balanceRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Test 3: STORE_MANAGER B requesting Warehouse A -> DENY (403 AccessDeniedException)")
    void storeManagerB_requestingOtherWarehouseBalances_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.balances(storeManagerB.getId().toString(), warehouseA.getId(), 0, 20)
        );

        assertTrue(ex.getMessage().contains("not authorized"));
        verify(balanceRepository, never()).findByWarehouse_Id(any(), any());
    }

    @Test
    @DisplayName("Test 4: CENTRAL_WAREHOUSE_MANAGER A requesting Warehouse B -> DENY (403 AccessDeniedException)")
    void centralWarehouseManagerA_requestingOtherWarehouse_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.balances(centralManagerA.getId().toString(), warehouseB.getId(), 0, 20)
        );

        assertTrue(ex.getMessage().contains("not authorized"));
        verify(balanceRepository, never()).findByWarehouse_Id(any(), any());
    }

    @Test
    @DisplayName("Test 5: CENTRAL_WAREHOUSE_MANAGER A requesting assigned Central Warehouse -> ALLOW")
    void centralWarehouseManagerA_canViewAssignedWarehouse_success() {
        Pageable pageable = PageRequest.of(0, 20);
        InventoryBalance centralBalance = createBalance(centralWarehouseA, "BATCH-CW-01", 500);
        when(balanceRepository.findByWarehouse_Id(eq(centralWarehouseA.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(centralBalance), pageable, 1));

        Page<InventoryBalanceResponse> result = controller.balances(
                centralManagerA.getId().toString(), centralWarehouseA.getId(), 0, 20
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(centralWarehouseA.getId(), result.getContent().getFirst().warehouseId());
        verify(balanceRepository, times(1)).findByWarehouse_Id(centralWarehouseA.getId(), pageable);
    }

    @Test
    @DisplayName("Test 6: STORE_MANAGER without assigned warehouse -> DENY (403 AccessDeniedException)")
    void storeManager_withoutAssignedWarehouse_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.balances(storeManagerNoWarehouse.getId().toString(), warehouseA.getId(), 0, 20)
        );

        assertTrue(ex.getMessage().contains("no assigned warehouse"));
        verify(balanceRepository, never()).findByWarehouse_Id(any(), any());
    }

    @Test
    @DisplayName("Test 7: STORE_MANAGER assigned to inactive warehouse -> DENY (403 AccessDeniedException)")
    void storeManager_assignedToInactiveWarehouse_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.balances(storeManagerInactiveWarehouse.getId().toString(), inactiveWarehouse.getId(), 0, 20)
        );

        assertTrue(ex.getMessage().contains("inactive"));
        verify(balanceRepository, never()).findByWarehouse_Id(any(), any());
    }

    @Test
    @DisplayName("Test 8: SUPER_ADMIN requesting Warehouse B -> ALLOW")
    void superAdmin_canViewAnyWarehouseBalances_success() {
        Pageable pageable = PageRequest.of(0, 20);
        when(balanceRepository.findByWarehouse_Id(eq(warehouseB.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(balanceB), pageable, 1));

        Page<InventoryBalanceResponse> result = controller.balances(
                superAdmin.getId().toString(), warehouseB.getId(), 0, 20
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(warehouseB.getId(), result.getContent().getFirst().warehouseId());
        verify(balanceRepository, times(1)).findByWarehouse_Id(warehouseB.getId(), pageable);
    }

    @Test
    @DisplayName("Test 9: AUDITOR requesting Warehouse B -> ALLOW")
    void auditor_canViewAnyWarehouseBalances_success() {
        Pageable pageable = PageRequest.of(0, 20);
        when(balanceRepository.findByWarehouse_Id(eq(warehouseB.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(balanceB), pageable, 1));

        Page<InventoryBalanceResponse> result = controller.balances(
                auditor.getId().toString(), warehouseB.getId(), 0, 20
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(warehouseB.getId(), result.getContent().getFirst().warehouseId());
        verify(balanceRepository, times(1)).findByWarehouse_Id(warehouseB.getId(), pageable);
    }

    @Test
    @DisplayName("Test 10: LOGISTICS_COORDINATOR attempting to view inventory balances -> DENY (403)")
    void logisticsCoordinator_deniedFromInventoryBalances() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.balances(logisticsCoordinator.getId().toString(), warehouseA.getId(), 0, 20)
        );

        assertTrue(ex.getMessage().contains("not authorized to view inventory balances"));
        verify(balanceRepository, never()).findByWarehouse_Id(any(), any());
    }

    @Test
    @DisplayName("Test 11: STORE_MANAGER omitting warehouseId parameter safely defaults to assigned warehouse")
    void storeManager_omittingWarehouseId_defaultsToAssignedWarehouse() {
        Pageable pageable = PageRequest.of(0, 20);
        when(balanceRepository.findByWarehouse_Id(eq(warehouseA.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(balanceA), pageable, 1));

        Page<InventoryBalanceResponse> result = controller.balances(
                storeManagerA.getId().toString(), null, 0, 20
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(warehouseA.getId(), result.getContent().getFirst().warehouseId());
        verify(balanceRepository, times(1)).findByWarehouse_Id(warehouseA.getId(), pageable);
    }

    @Test
    @DisplayName("Test 12: SUPER_ADMIN omitting warehouseId parameter queries global unconstrained inventory")
    void superAdmin_omittingWarehouseId_queriesGlobalInventory() {
        Pageable pageable = PageRequest.of(0, 20);
        when(balanceRepository.findAllWithDetails(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(balanceA, balanceB), pageable, 2));

        Page<InventoryBalanceResponse> result = controller.balances(
                superAdmin.getId().toString(), null, 0, 20
        );

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        verify(balanceRepository, times(1)).findAllWithDetails(pageable);
        verify(balanceRepository, never()).findByWarehouse_Id(any(), any());
    }

    @Test
    @DisplayName("Test 13: Pagination metadata and counts reflect only authorized facility records")
    void paginationAndCounts_reflectsOnlyAuthorizedFacilityRecords() {
        Pageable pageable = PageRequest.of(0, 20);
        when(balanceRepository.findByWarehouse_Id(eq(warehouseA.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(balanceA), pageable, 1));

        Page<InventoryBalanceResponse> result = controller.balances(
                storeManagerA.getId().toString(), warehouseA.getId(), 0, 20
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals(1, result.getContent().size());
        assertEquals(balanceA.getId(), result.getContent().getFirst().id());
    }

    @Test
    @DisplayName("Test 14: Unauthorized read attempt causes zero database or ledger mutation")
    void unauthorizedReadAttempt_causesNoDatabaseOrLedgerSideEffects() {
        assertThrows(AccessDeniedException.class, () ->
                controller.balances(storeManagerA.getId().toString(), warehouseB.getId(), 0, 20)
        );

        verify(balanceRepository, never()).save(any());
        verify(balanceRepository, never()).delete(any());
        verify(journalRepository, never()).save(any());
        verifyNoInteractions(inboundService);
        verifyNoInteractions(fefoEngine);
    }

    @Test
    @DisplayName("Test 15: Attack replay - STORE_MANAGER A exploits warehouseId=B query parameter -> HTTP 403")
    void attackReplay_storeManagerAExploitsWarehouseBParam_controllerThrows403() {
        // Conceptual exploit:
        // Actor: STORE_MANAGER assigned to Warehouse A
        // Request: GET /api/v1/inventory/balances?warehouseId=Warehouse-B
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.balances(storeManagerA.getId().toString(), warehouseB.getId(), 0, 20)
        );

        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("User is not authorized to view inventory balances for warehouse: " + warehouseB.getId()));
        verify(balanceRepository, never()).findByWarehouse_Id(warehouseB.getId(), PageRequest.of(0, 20));
    }

    // --- Helper Methods ---

    private Warehouse createWarehouse(String code, String name, WarehouseType type, WarehouseStatus status) {
        Warehouse w = new Warehouse(code, name, type, "Address " + code,
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

    private InventoryBalance createBalance(Warehouse warehouse, String batchNumber, int quantity) {
        Medicine medicine = new Medicine("MED-001", "Amoxicillin", null, null, "CAPSULE", "500mg", "BOX", null, 0, 90, null);
        Supplier supplier = new Supplier("PharmaCorp", "PHARM", null, null, null);
        Batch batch = new Batch(batchNumber, medicine, supplier, LocalDate.now().minusDays(10), LocalDate.now().plusDays(365), quantity, BatchStatus.ACTIVE);
        StorageLocation location = new StorageLocation(warehouse, "A", "1", "1", "A-1-1");

        InventoryBalance balance = new InventoryBalance(warehouse, batch, location);
        ReflectionTestUtils.setField(balance, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(balance, "version", 1L);
        balance.receive(quantity);
        return balance;
    }
}
