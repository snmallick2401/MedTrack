package com.medtrack.transfer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.medtrack.auth.entity.Role;
import com.medtrack.inventory.service.FefoAllocationEngine;
import com.medtrack.inventory.service.InventoryMovementService;
import com.medtrack.medicine.repository.MedicineRepository;
import com.medtrack.shared.exception.NotFoundException;
import com.medtrack.shared.idempotency.IdempotencyService;
import com.medtrack.shipment.dto.ShipmentResponse;
import com.medtrack.shipment.entity.Shipment;
import com.medtrack.shipment.entity.ShipmentStatus;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.shipment.service.ShipmentAuthorizationService;
import com.medtrack.shipment.service.ShipmentService;
import com.medtrack.tracking.dto.TrackingResponse;
import com.medtrack.tracking.entity.TrackingEvent;
import com.medtrack.tracking.provider.ShipmentTrackingProvider;
import com.medtrack.tracking.repository.TrackingEventRepository;
import com.medtrack.tracking.service.TrackingService;
import com.medtrack.transfer.dto.TransferResponse;
import com.medtrack.transfer.entity.StockTransfer;
import com.medtrack.transfer.entity.TransferStatus;
import com.medtrack.transfer.repository.StockTransferRepository;
import com.medtrack.user.entity.User;
import com.medtrack.user.entity.UserStatus;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.repository.StorageLocationRepository;
import com.medtrack.warehouse.entity.Warehouse;
import com.medtrack.warehouse.entity.WarehouseStatus;
import com.medtrack.warehouse.entity.WarehouseType;
import com.medtrack.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class TransferReadAuthorizationSecurityTest {

    private TransferAuthorizationService transferAuth;
    private ShipmentAuthorizationService shipmentAuth;

    private TransferService transferService;
    private ShipmentService shipmentService;
    private TrackingService trackingService;

    private StockTransferRepository transferRepo;
    private ShipmentRepository shipmentRepo;
    private UserRepository userRepo;
    private TrackingEventRepository trackingEventRepo;

    private Warehouse warehouseA;
    private Warehouse warehouseB;
    private Warehouse warehouseC;
    private Warehouse inactiveWarehouse;

    private User storeManagerA;
    private User storeManagerB;
    private User storeManagerNoWarehouse;
    private User storeManagerInactiveWarehouse;
    private User superAdmin;
    private User auditor;
    private User logisticsCoordinator;

    private StockTransfer transferDestinedForA;
    private StockTransfer transferSourcedFromA;
    private StockTransfer transferBetweenBAndC;

    private Shipment shipmentForA;
    private Shipment shipmentBetweenBAndC;

    @BeforeEach
    void setUp() {
        transferAuth = new TransferAuthorizationService();
        shipmentAuth = new ShipmentAuthorizationService();

        transferRepo = mock(StockTransferRepository.class);
        shipmentRepo = mock(ShipmentRepository.class);
        userRepo = mock(UserRepository.class);
        trackingEventRepo = mock(TrackingEventRepository.class);

        MedicineRepository medicineRepo = mock(MedicineRepository.class);
        WarehouseRepository warehouseRepo = mock(WarehouseRepository.class);
        FefoAllocationEngine fefo = mock(FefoAllocationEngine.class);
        InventoryMovementService inventory = mock(InventoryMovementService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        EntityManager em = mock(EntityManager.class);
        StorageLocationRepository locationRepo = mock(StorageLocationRepository.class);
        ShipmentTrackingProvider trackingProvider = mock(ShipmentTrackingProvider.class);

        transferService = new TransferService(
                transferRepo, medicineRepo, warehouseRepo, userRepo, fefo, inventory, shipmentRepo, idempotency, em, transferAuth
        );

        shipmentService = new ShipmentService(
                shipmentRepo, transferRepo, userRepo, locationRepo, inventory, idempotency, em, shipmentAuth
        );

        trackingService = new TrackingService(
                shipmentRepo, trackingEventRepo, userRepo, trackingProvider, shipmentAuth
        );

        // Warehouses
        warehouseA = createWarehouse("DS01", "Store North", WarehouseStatus.ACTIVE);
        warehouseB = createWarehouse("DS02", "Store South", WarehouseStatus.ACTIVE);
        warehouseC = createWarehouse("CW01", "Central Wh", WarehouseStatus.ACTIVE);
        inactiveWarehouse = createWarehouse("DS03", "Store Inactive", WarehouseStatus.INACTIVE);

        // Roles
        Role roleStoreManager = createRole("STORE_MANAGER");
        Role roleSuperAdmin = createRole("SUPER_ADMIN");
        Role roleAuditor = createRole("AUDITOR");
        Role roleLogistics = createRole("LOGISTICS_COORDINATOR");

        // Users
        storeManagerA = createUser("mgr.a@medtrack.local", roleStoreManager, warehouseA);
        storeManagerB = createUser("mgr.b@medtrack.local", roleStoreManager, warehouseB);
        storeManagerNoWarehouse = createUser("mgr.nowh@medtrack.local", roleStoreManager, null);
        storeManagerInactiveWarehouse = createUser("mgr.inact@medtrack.local", roleStoreManager, inactiveWarehouse);
        superAdmin = createUser("admin@medtrack.local", roleSuperAdmin, null);
        auditor = createUser("auditor@medtrack.local", roleAuditor, null);
        logisticsCoordinator = createUser("logistics@medtrack.local", roleLogistics, null);

        when(userRepo.findById(storeManagerA.getId())).thenReturn(Optional.of(storeManagerA));
        when(userRepo.findById(storeManagerB.getId())).thenReturn(Optional.of(storeManagerB));
        when(userRepo.findById(storeManagerNoWarehouse.getId())).thenReturn(Optional.of(storeManagerNoWarehouse));
        when(userRepo.findById(storeManagerInactiveWarehouse.getId())).thenReturn(Optional.of(storeManagerInactiveWarehouse));
        when(userRepo.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepo.findById(auditor.getId())).thenReturn(Optional.of(auditor));
        when(userRepo.findById(logisticsCoordinator.getId())).thenReturn(Optional.of(logisticsCoordinator));

        // Transfers
        transferDestinedForA = createTransfer("TRF-001", warehouseC, warehouseA, storeManagerA);
        transferSourcedFromA = createTransfer("TRF-002", warehouseA, warehouseB, storeManagerA);
        transferBetweenBAndC = createTransfer("TRF-003", warehouseC, warehouseB, storeManagerB);

        // Shipments
        shipmentForA = createShipment("SHP-001", transferDestinedForA);
        shipmentBetweenBAndC = createShipment("SHP-002", transferBetweenBAndC);
    }

    @Test
    @DisplayName("Test 1: Store Manager A listing transfers receives ONLY facility-scoped records")
    void listTransfersScopedToFacility() {
        Pageable pageable = PageRequest.of(0, 20);
        List<StockTransfer> authorizedTransfers = List.of(transferDestinedForA, transferSourcedFromA);
        when(transferRepo.findByWarehouse(eq(warehouseA.getId()), eq(pageable)))
                .thenReturn(new PageImpl<>(authorizedTransfers, pageable, 2));

        Page<TransferResponse> result = transferService.list(storeManagerA.getId(), pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        verify(transferRepo, times(1)).findByWarehouse(warehouseA.getId(), pageable);
        verify(transferRepo, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Test 2: Store Manager A listing transfers does NOT see unrelated cross-facility transfers")
    void listTransfersExcludesCrossFacilityRecords() {
        Pageable pageable = PageRequest.of(0, 20);
        when(transferRepo.findByWarehouse(eq(warehouseA.getId()), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(transferDestinedForA), pageable, 1));

        Page<TransferResponse> result = transferService.list(storeManagerA.getId(), pageable);

        assertTrue(result.getContent().stream().noneMatch(t -> t.id().equals(transferBetweenBAndC.getId())),
                "Transfers between Warehouse B and C must not appear in Manager A list");
    }

    @Test
    @DisplayName("Test 3: Store Manager A viewing transfer detail where Warehouse A is destination succeeds")
    void getTransferDetailAsDestinationAllowed() {
        when(transferRepo.findById(transferDestinedForA.getId())).thenReturn(Optional.of(transferDestinedForA));

        TransferResponse response = transferService.get(storeManagerA.getId(), transferDestinedForA.getId());

        assertNotNull(response);
        assertEquals(transferDestinedForA.getId(), response.id());
    }

    @Test
    @DisplayName("Test 4: Store Manager A viewing transfer detail where Warehouse A is source succeeds")
    void getTransferDetailAsSourceAllowed() {
        when(transferRepo.findById(transferSourcedFromA.getId())).thenReturn(Optional.of(transferSourcedFromA));

        TransferResponse response = transferService.get(storeManagerA.getId(), transferSourcedFromA.getId());

        assertNotNull(response);
        assertEquals(transferSourcedFromA.getId(), response.id());
    }

    @Test
    @DisplayName("Test 5: Store Manager A viewing transfer detail for unrelated transfer (B <-> C) is DENIED (403)")
    void getTransferDetailUnrelatedFacilityDenied() {
        when(transferRepo.findById(transferBetweenBAndC.getId())).thenReturn(Optional.of(transferBetweenBAndC));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> transferService.get(storeManagerA.getId(), transferBetweenBAndC.getId())
        );

        assertTrue(ex.getMessage().contains("outside assigned facility"));
    }

    @Test
    @DisplayName("Test 6: Store Manager A listing shipments receives ONLY shipments involving Warehouse A")
    void listShipmentsScopedToFacility() {
        Pageable pageable = PageRequest.of(0, 20);
        when(shipmentRepo.findByWarehouse(eq(warehouseA.getId()), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(shipmentForA), pageable, 1));

        Page<ShipmentResponse> result = shipmentService.list(storeManagerA.getId(), pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(shipmentForA.getId(), result.getContent().get(0).id());
        verify(shipmentRepo, times(1)).findByWarehouse(warehouseA.getId(), pageable);
        verify(shipmentRepo, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Test 7: Store Manager A viewing shipment detail for Warehouse A succeeds")
    void getShipmentDetailAuthorized() {
        when(shipmentRepo.findById(shipmentForA.getId())).thenReturn(Optional.of(shipmentForA));

        ShipmentResponse response = shipmentService.get(storeManagerA.getId(), shipmentForA.getId());

        assertNotNull(response);
        assertEquals(shipmentForA.getId(), response.id());
    }

    @Test
    @DisplayName("Test 8: Store Manager A viewing shipment detail for unrelated shipment is DENIED (403)")
    void getShipmentDetailUnauthorizedDenied() {
        when(shipmentRepo.findById(shipmentBetweenBAndC.getId())).thenReturn(Optional.of(shipmentBetweenBAndC));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> shipmentService.get(storeManagerA.getId(), shipmentBetweenBAndC.getId())
        );

        assertTrue(ex.getMessage().contains("outside assigned facility"));
    }

    @Test
    @DisplayName("Test 9: Pagination isolation: totalElements and totalPages reflect only authorized records")
    void paginationMetadataDoesNotLeakCrossFacilityTotals() {
        Pageable pageable = PageRequest.of(0, 10);
        StockTransfer thirdTransfer = createTransfer("TRF-004", warehouseA, warehouseC, storeManagerA);
        List<StockTransfer> facilityTransfers = List.of(transferDestinedForA, transferSourcedFromA, thirdTransfer);
        // Entire system has 150 transfers, but Warehouse A only has 3
        when(transferRepo.findByWarehouse(eq(warehouseA.getId()), eq(pageable)))
                .thenReturn(new PageImpl<>(facilityTransfers, pageable, 3));

        Page<TransferResponse> result = transferService.list(storeManagerA.getId(), pageable);

        assertEquals(3, result.getTotalElements(), "totalElements must not reflect cross-facility enterprise count");
        assertEquals(1, result.getTotalPages());
    }

    @Test
    @DisplayName("Test 10: Resource enumeration: Non-existent transfer throws NotFoundException (404)")
    void nonExistentTransferThrowsNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(transferRepo.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> transferService.get(storeManagerA.getId(), nonExistentId)
        );
    }

    @Test
    @DisplayName("Test 11: Global roles (SUPER_ADMIN, AUDITOR, LOGISTICS_COORDINATOR) retain global visibility")
    void globalRolesRetainEnterpriseVisibility() {
        Pageable pageable = PageRequest.of(0, 20);
        List<StockTransfer> allTransfers = List.of(transferDestinedForA, transferSourcedFromA, transferBetweenBAndC);
        when(transferRepo.findAll(pageable)).thenReturn(new PageImpl<>(allTransfers, pageable, 3));
        when(transferRepo.findById(transferBetweenBAndC.getId())).thenReturn(Optional.of(transferBetweenBAndC));

        // SUPER_ADMIN
        Page<TransferResponse> adminList = transferService.list(superAdmin.getId(), pageable);
        assertEquals(3, adminList.getTotalElements());
        assertDoesNotThrow(() -> transferService.get(superAdmin.getId(), transferBetweenBAndC.getId()));

        // AUDITOR
        Page<TransferResponse> auditorList = transferService.list(auditor.getId(), pageable);
        assertEquals(3, auditorList.getTotalElements());
        assertDoesNotThrow(() -> transferService.get(auditor.getId(), transferBetweenBAndC.getId()));

        // LOGISTICS_COORDINATOR
        Page<TransferResponse> logisticsList = transferService.list(logisticsCoordinator.getId(), pageable);
        assertEquals(3, logisticsList.getTotalElements());
        assertDoesNotThrow(() -> transferService.get(logisticsCoordinator.getId(), transferBetweenBAndC.getId()));
    }

    @Test
    @DisplayName("Test 12: Indirect path isolation: Manager A querying tracking history for Shipment B is DENIED (403)")
    void indirectTrackingPathIsolation() {
        when(shipmentRepo.findById(shipmentBetweenBAndC.getId())).thenReturn(Optional.of(shipmentBetweenBAndC));

        AccessDeniedException ex = assertThrows(
                AccessDeniedException.class,
                () -> trackingService.history(storeManagerA.getId(), shipmentBetweenBAndC.getId())
        );

        assertTrue(ex.getMessage().contains("outside assigned facility"));
        verify(trackingEventRepo, never()).findByShipment_IdOrderByEventTimestampAsc(any());
    }

    @Test
    @DisplayName("Test 13: Fail-closed on missing or inactive assigned warehouse")
    void missingOrInactiveWarehouseFailsClosed() {
        Pageable pageable = PageRequest.of(0, 20);

        // Missing warehouse -> empty list
        Page<TransferResponse> noWhList = transferService.list(storeManagerNoWarehouse.getId(), pageable);
        assertEquals(0, noWhList.getTotalElements());

        // Inactive warehouse -> empty list
        Page<TransferResponse> inactList = transferService.list(storeManagerInactiveWarehouse.getId(), pageable);
        assertEquals(0, inactList.getTotalElements());

        // Detail lookup -> AccessDeniedException
        when(transferRepo.findById(transferDestinedForA.getId())).thenReturn(Optional.of(transferDestinedForA));
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.get(storeManagerNoWarehouse.getId(), transferDestinedForA.getId())
        );
        assertThrows(
                AccessDeniedException.class,
                () -> transferService.get(storeManagerInactiveWarehouse.getId(), transferDestinedForA.getId())
        );
    }

    @Test
    @DisplayName("Test 14: Unauthorized read attempts cause zero state mutations")
    void unauthorizedReadAttemptsAreSideEffectFree() {
        when(transferRepo.findById(transferBetweenBAndC.getId())).thenReturn(Optional.of(transferBetweenBAndC));
        when(shipmentRepo.findById(shipmentBetweenBAndC.getId())).thenReturn(Optional.of(shipmentBetweenBAndC));

        assertThrows(AccessDeniedException.class, () -> transferService.get(storeManagerA.getId(), transferBetweenBAndC.getId()));
        assertThrows(AccessDeniedException.class, () -> shipmentService.get(storeManagerA.getId(), shipmentBetweenBAndC.getId()));

        assertEquals(TransferStatus.DISPATCHED, transferBetweenBAndC.getStatus());
        assertEquals(ShipmentStatus.PREPARING, shipmentBetweenBAndC.getStatus());
        verify(shipmentRepo, never()).save(any());
        verify(transferRepo, never()).save(any());
    }

    // --- Helpers ---

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

    private Shipment createShipment(String number, StockTransfer transfer) {
        Shipment s = new Shipment(number, transfer, "FastFreight", "TRK-001", "John Driver", "+1-555-0199", "VEH-01", Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        return s;
    }
}
