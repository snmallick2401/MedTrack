package com.medtrack.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.medtrack.auth.entity.Role;
import com.medtrack.batch.entity.Batch;
import com.medtrack.batch.entity.BatchStatus;
import com.medtrack.inventory.entity.InventoryBalance;
import com.medtrack.inventory.repository.InventoryBalanceRepository;
import com.medtrack.inventory.service.InventoryAuthorizationService;
import com.medtrack.medicine.entity.Medicine;
import com.medtrack.report.controller.ReportController;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class ReportAuthorizationSecurityTest {

    private InventoryAuthorizationService authorizationService;
    private ReportService reportService;
    private ReportController controller;

    private InventoryBalanceRepository balanceRepository;
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
        userRepository = mock(UserRepository.class);

        reportService = new ReportService(balanceRepository, authorizationService);
        controller = new ReportController(reportService, userRepository);

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

        // Balances: balanceA belongs to Warehouse A, balanceB belongs to Warehouse B
        // Expiring in 20 days (critical expiry)
        balanceA = createBalance(warehouseA, "BATCH-DS01-A", 150, LocalDate.now().plusDays(20));
        balanceB = createBalance(warehouseB, "BATCH-DS02-B", 300, LocalDate.now().plusDays(15));
    }

    @Test
    @DisplayName("Test 1: STORE_MANAGER A requesting inventory report -> only Warehouse A data in CSV")
    void storeManager_inventoryReport_containsOnlyAssignedWarehouseData() {
        when(balanceRepository.findAllByWarehouse_Id(warehouseA.getId())).thenReturn(List.of(balanceA));

        ResponseEntity<byte[]> response = controller.inventoryCsv(storeManagerA.getId().toString(), null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("DS01"), "CSV must contain Warehouse A code");
        assertTrue(csv.contains("BATCH-DS01-A"), "CSV must contain Warehouse A batch");
        assertFalse(csv.contains("DS02"), "CSV must NOT contain Warehouse B code");
        assertFalse(csv.contains("BATCH-DS02-B"), "CSV must NOT contain Warehouse B batch");
        verify(balanceRepository, times(1)).findAllByWarehouse_Id(warehouseA.getId());
        verify(balanceRepository, never()).findAll();
    }

    @Test
    @DisplayName("Test 2: STORE_MANAGER A attempting unauthorized inventory export of Warehouse B -> DENY (403)")
    void storeManager_unauthorizedInventoryExportForeignWarehouse_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.inventoryCsv(storeManagerA.getId().toString(), warehouseB.getId())
        );

        assertTrue(ex.getMessage().contains("not authorized"));
        verify(balanceRepository, never()).findAllByWarehouse_Id(any());
        verify(balanceRepository, never()).findAll();
    }

    @Test
    @DisplayName("Test 3: STORE_MANAGER A requesting expiry report CSV -> only Warehouse A records in CSV")
    void storeManager_expiryReportCsv_containsOnlyAssignedWarehouseRecords() {
        when(balanceRepository.findAllByWarehouse_Id(warehouseA.getId())).thenReturn(List.of(balanceA));

        ResponseEntity<byte[]> response = controller.expiryReportCsv(storeManagerA.getId().toString(), 90, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("DS01"));
        assertTrue(csv.contains("BATCH-DS01-A"));
        assertFalse(csv.contains("DS02"));
        assertFalse(csv.contains("BATCH-DS02-B"));
        verify(balanceRepository, times(1)).findAllByWarehouse_Id(warehouseA.getId());
    }

    @Test
    @DisplayName("Test 4: STORE_MANAGER A requesting expiry data from Warehouse B -> DENY (403)")
    void storeManager_unauthorizedExpiryExportForeignWarehouse_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.expiryReportData(storeManagerA.getId().toString(), 90, warehouseB.getId())
        );

        assertTrue(ex.getMessage().contains("not authorized"));
        verify(balanceRepository, never()).findAllByWarehouse_Id(any());
    }

    @Test
    @DisplayName("Test 5: CENTRAL_WAREHOUSE_MANAGER A -> inventory report scoped strictly to assigned warehouse")
    void centralWarehouseManager_inventoryReport_scopedToAssignedWarehouse() {
        InventoryBalance centralBalance = createBalance(centralWarehouseA, "BATCH-CW01-C", 500, LocalDate.now().plusDays(25));
        when(balanceRepository.findAllByWarehouse_Id(centralWarehouseA.getId())).thenReturn(List.of(centralBalance));

        ResponseEntity<byte[]> response = controller.inventoryCsv(centralManagerA.getId().toString(), null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("CW01"));
        assertTrue(csv.contains("BATCH-CW01-C"));
        assertFalse(csv.contains("DS01"));
        assertFalse(csv.contains("DS02"));
        verify(balanceRepository, times(1)).findAllByWarehouse_Id(centralWarehouseA.getId());
    }

    @Test
    @DisplayName("Test 6: Cross-facility parameter manipulation (warehouseId=B, actor assigned A) -> DENY (403)")
    void crossFacilityParameterManipulation_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.expiryReportCsv(storeManagerA.getId().toString(), 90, warehouseB.getId())
        );

        assertTrue(ex.getMessage().contains("not authorized"));
        verify(balanceRepository, never()).findAllByWarehouse_Id(any());
    }

    @Test
    @DisplayName("Test 7: STORE_MANAGER with missing assigned warehouse -> DENY (403)")
    void storeManager_missingAssignedWarehouse_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.inventoryCsv(storeManagerNoWarehouse.getId().toString(), null)
        );

        assertTrue(ex.getMessage().contains("no assigned warehouse"));
        verify(balanceRepository, never()).findAllByWarehouse_Id(any());
    }

    @Test
    @DisplayName("Test 8: STORE_MANAGER with inactive assigned warehouse -> DENY (403)")
    void storeManager_inactiveAssignedWarehouse_deniedWith403() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.inventoryCsv(storeManagerInactiveWarehouse.getId().toString(), null)
        );

        assertTrue(ex.getMessage().contains("inactive"));
        verify(balanceRepository, never()).findAllByWarehouse_Id(any());
    }

    @Test
    @DisplayName("Test 9: SUPER_ADMIN requesting global report without warehouseId -> ALLOW (all warehouses)")
    void superAdmin_globalInventoryAndExpiryReport_allowed() {
        when(balanceRepository.findAllWithDetailsList()).thenReturn(List.of(balanceA, balanceB));

        ResponseEntity<byte[]> invResponse = controller.inventoryCsv(superAdmin.getId().toString(), null);
        String invCsv = new String(invResponse.getBody(), StandardCharsets.UTF_8);
        assertTrue(invCsv.contains("DS01"));
        assertTrue(invCsv.contains("DS02"));

        List<Map<String, Object>> expiryData = controller.expiryReportData(superAdmin.getId().toString(), 90, null);
        assertEquals(2, expiryData.size());
        assertTrue(expiryData.stream().anyMatch(i -> "DS01".equals(i.get("warehouseCode"))));
        assertTrue(expiryData.stream().anyMatch(i -> "DS02".equals(i.get("warehouseCode"))));
    }

    @Test
    @DisplayName("Test 10: AUDITOR requesting targeted warehouse report -> ALLOW (targeted warehouse only)")
    void auditor_targetedWarehouseReport_allowed() {
        when(balanceRepository.findAllByWarehouse_Id(warehouseB.getId())).thenReturn(List.of(balanceB));

        ResponseEntity<byte[]> response = controller.inventoryCsv(auditor.getId().toString(), warehouseB.getId());
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("DS02"));
        assertFalse(csv.contains("DS01"));
        verify(balanceRepository, times(1)).findAllByWarehouse_Id(warehouseB.getId());
    }

    @Test
    @DisplayName("Test 11: LOGISTICS_COORDINATOR attempting to export reports -> DENY (403)")
    void logisticsCoordinator_deniedFromAllReports() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.inventoryCsv(logisticsCoordinator.getId().toString(), warehouseA.getId())
        );

        assertTrue(ex.getMessage().contains("not authorized to generate or export reports"));
        verify(balanceRepository, never()).findAllByWarehouse_Id(any());
    }

    @Test
    @DisplayName("Test 12: Expiry report data reflects only authorized facility records")
    void storeManager_expiryReportData_containsOnlyAssignedWarehouseItems() {
        when(balanceRepository.findAllByWarehouse_Id(warehouseA.getId())).thenReturn(List.of(balanceA));

        List<Map<String, Object>> result = controller.expiryReportData(storeManagerA.getId().toString(), 90, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("DS01", result.getFirst().get("warehouseCode"));
        assertEquals("BATCH-DS01-A", result.getFirst().get("batchNumber"));
        verify(balanceRepository, times(1)).findAllByWarehouse_Id(warehouseA.getId());
    }

    @Test
    @DisplayName("Test 13: CSV content verified completely free of foreign warehouse, batch, or location data")
    void csvContent_verifiedFreeOfForeignWarehouseOrBinData() {
        when(balanceRepository.findAllByWarehouse_Id(warehouseA.getId())).thenReturn(List.of(balanceA));

        ResponseEntity<byte[]> invResponse = controller.inventoryCsv(storeManagerA.getId().toString(), null);
        String invCsv = new String(invResponse.getBody(), StandardCharsets.UTF_8);

        assertFalse(invCsv.contains(warehouseB.getId().toString()));
        assertFalse(invCsv.contains("DS02"));
        assertFalse(invCsv.contains("BATCH-DS02-B"));

        ResponseEntity<byte[]> expResponse = controller.expiryReportCsv(storeManagerA.getId().toString(), 90, null);
        String expCsv = new String(expResponse.getBody(), StandardCharsets.UTF_8);

        assertFalse(expCsv.contains(warehouseB.getId().toString()));
        assertFalse(expCsv.contains("DS02"));
        assertFalse(expCsv.contains("BATCH-DS02-B"));
    }

    @Test
    @DisplayName("Test 14: Unauthorized export causes no sensitive database query or report generation")
    void unauthorizedExport_causesNoDataQueryOrFileGeneration() {
        assertThrows(AccessDeniedException.class, () ->
                controller.inventoryCsv(storeManagerA.getId().toString(), warehouseB.getId())
        );

        verify(balanceRepository, never()).findAllByWarehouse_Id(any());
        verify(balanceRepository, never()).findAll();
        verify(balanceRepository, never()).findAllWithDetailsList();
    }

    @Test
    @DisplayName("Test 15: Attack replay - STORE_MANAGER A exploits warehouseId=B parameter -> HTTP 403")
    void attackReplay_storeManagerRequestingForeignWarehouseReport_returns403() {
        // Conceptual exploit:
        // Actor: STORE_MANAGER assigned to Warehouse A
        // Request: GET /api/v1/reports/inventory?warehouseId=Warehouse-B
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                controller.inventoryCsv(storeManagerA.getId().toString(), warehouseB.getId())
        );

        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("User is not authorized to export reports for warehouse: " + warehouseB.getId()));
        verify(balanceRepository, never()).findAllByWarehouse_Id(warehouseB.getId());
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

    private InventoryBalance createBalance(Warehouse warehouse, String batchNumber, int quantity, LocalDate expiryDate) {
        Medicine medicine = new Medicine("MED-001", "Amoxicillin", null, null, "CAPSULE", "500mg", "BOX", null, 0, 90, null);
        ReflectionTestUtils.setField(medicine, "id", UUID.randomUUID());
        Supplier supplier = new Supplier("PharmaCorp", "PHARM", null, null, null);
        ReflectionTestUtils.setField(supplier, "id", UUID.randomUUID());
        Batch batch = new Batch(batchNumber, medicine, supplier, LocalDate.now().minusDays(10), expiryDate, quantity, BatchStatus.ACTIVE);
        ReflectionTestUtils.setField(batch, "id", UUID.randomUUID());
        StorageLocation location = new StorageLocation(warehouse, "A", "1", "1", "A-1-1");
        ReflectionTestUtils.setField(location, "id", UUID.randomUUID());

        InventoryBalance balance = new InventoryBalance(warehouse, batch, location);
        ReflectionTestUtils.setField(balance, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(balance, "version", 1L);
        balance.receive(quantity);
        return balance;
    }
}
