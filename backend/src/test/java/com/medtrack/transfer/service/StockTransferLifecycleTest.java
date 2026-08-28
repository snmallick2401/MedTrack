package com.medtrack.transfer.service;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.batch.entity.*;
import com.medtrack.inventory.dto.InboundReceiptRequest;
import com.medtrack.inventory.entity.*;
import com.medtrack.inventory.repository.*;
import com.medtrack.inventory.service.InboundService;
import com.medtrack.medicine.entity.*;
import com.medtrack.medicine.repository.*;
import com.medtrack.shared.exception.ConflictException;
import com.medtrack.shipment.dto.*;
import com.medtrack.shipment.entity.*;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.shipment.service.ShipmentService;
import com.medtrack.supplier.entity.Supplier;
import com.medtrack.supplier.repository.SupplierRepository;
import com.medtrack.transfer.dto.*;
import com.medtrack.transfer.entity.*;
import com.medtrack.transfer.repository.StockTransferRepository;
import com.medtrack.user.entity.User;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.entity.*;
import com.medtrack.warehouse.repository.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StockTransferLifecycleTest {

    @Autowired private TransferService transferService;
    @Autowired private ShipmentService shipmentService;
    @Autowired private InboundService inboundService;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private InventoryJournalEntryRepository journalRepository;
    @Autowired private InventoryLedgerLineRepository ledgerLineRepository;
    @Autowired private MedicineRepository medicineRepository;
    @Autowired private MedicineCategoryRepository categoryRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private UserRepository userRepository;

    private User superAdmin;
    private Warehouse centralWarehouse;
    private Warehouse regionalWarehouse;
    private StorageLocation centralBin;
    private StorageLocation regionalBin;
    private Supplier supplier;
    private MedicineCategory category;

    @BeforeEach
    void setUp() {
        superAdmin = userRepository.findAll().stream()
            .filter(u -> "SUPER_ADMIN".equals(u.getRole().getName()))
            .findFirst().orElseThrow();

        centralWarehouse = warehouseRepository.findAll().stream()
            .filter(w -> w.getType() == WarehouseType.CENTRAL_WAREHOUSE && w.getStatus() == WarehouseStatus.ACTIVE)
            .findFirst().orElseThrow();

        regionalWarehouse = warehouseRepository.findAll().stream()
            .filter(w -> w.getType() == WarehouseType.DISTRIBUTION_STORE && w.getStatus() == WarehouseStatus.ACTIVE)
            .findFirst().orElseGet(() ->
                warehouseRepository.save(new Warehouse("RW-TEST-01", "Regional Test Store", WarehouseType.DISTRIBUTION_STORE, "200 Market St", null, null, "+2000", WarehouseStatus.ACTIVE))
            );

        centralBin = storageLocationRepository.findByWarehouse_Id(centralWarehouse.getId()).stream()
            .findFirst().orElseGet(() ->
                storageLocationRepository.save(new StorageLocation(centralWarehouse, "ZONE-C", "RACK-01", "SHELF-01", "BIN-CW-01"))
            );

        regionalBin = storageLocationRepository.findByWarehouse_Id(regionalWarehouse.getId()).stream()
            .findFirst().orElseGet(() ->
                storageLocationRepository.save(new StorageLocation(regionalWarehouse, "ZONE-R", "RACK-01", "SHELF-01", "BIN-RW-01"))
            );

        supplier = supplierRepository.findAll().stream().findFirst().orElseGet(() ->
            supplierRepository.save(new Supplier("PharmaCorp Global", "SUP-PHARMA-01", "orders@pharmacorp.local", "+1-555-0199", "100 Pharma Blvd, Boston, MA"))
        );
        category = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
            categoryRepository.save(new MedicineCategory("ANTIBIOTIC", "Antibiotic", "Antibiotic medications"))
        );
    }

    @Test
    void testFullLifecycleAndAc04DuplicateReceiveProtection() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        Medicine medicine = medicineRepository.save(new Medicine(
            String.format("MED-LIFE-%05d", new Random().nextInt(90000) + 10000), "Amoxicillin Test", "Amoxil", category, "TABLET", "500mg", "BOX", "ROOM_TEMP", 10, 90, "ACTIVE"
        ));

        // 1. Inbound 300 units into central warehouse
        String batchNum = "BAT-LIFE-" + testSuffix;
        inboundService.receive(superAdmin.getId(), "IDEMP-INB-" + testSuffix, new InboundReceiptRequest(
            supplier.getId(), centralWarehouse.getId(), centralBin.getId(), medicine.getId(),
            batchNum, LocalDate.now().minusDays(30), LocalDate.now().plusDays(200), 300
        ));

        InventoryBalance initialBalance = balanceRepository.findByWarehouse_Id(centralWarehouse.getId(), org.springframework.data.domain.Pageable.unpaged())
            .stream().filter(b -> b.getBatch().getBatchNumber().equals(batchNum)).findFirst().orElseThrow();
        UUID batchId = initialBalance.getBatch().getId();
        assertEquals(300, initialBalance.getAvailableQuantity());
        assertEquals(0, initialBalance.getReservedQuantity());

        // 2. Create Stock Transfer for 100 units
        TransferRequest req = new TransferRequest(regionalWarehouse.getId(), List.of(new TransferRequest.Item(medicine.getId(), 100)), "Lifecycle transfer");
        TransferResponse transfer = transferService.create(superAdmin.getId(), "IDEMP-TRF-" + testSuffix, req);
        assertEquals("REQUESTED", transfer.status());

        // 3. Approve Transfer
        transfer = transferService.approve(superAdmin.getId(), transfer.id());
        assertEquals("APPROVED", transfer.status());

        // 4. Allocate Stock via FEFO
        transfer = transferService.allocate(superAdmin.getId(), transfer.id(), "IDEMP-ALLOC-" + testSuffix);
        assertEquals("ALLOCATED", transfer.status());
        InventoryBalance balanceAfterAlloc = balanceRepository.findById(initialBalance.getId()).orElseThrow();
        assertEquals(200, balanceAfterAlloc.getAvailableQuantity());
        assertEquals(100, balanceAfterAlloc.getReservedQuantity());

        // 5. Pick Stock
        PickRequest pickReq = new PickRequest(List.of(new PickRequest.Item(batchId, 100)));
        transfer = transferService.pick(superAdmin.getId(), transfer.id(), pickReq);
        assertEquals("PICKED", transfer.status());

        // 6. Pack Stock
        transfer = transferService.pack(transfer.id());
        assertEquals("PACKED", transfer.status());

        // 7. Create Shipment & verify ShipmentItem mapping
        ShipmentRequest shipReq = new ShipmentRequest(
            transfer.id(), "MediLogistics", "TRK-LIFE-" + testSuffix, "Driver John", "+1987654321", "TRUCK-01", Instant.now().plusSeconds(86400)
        );
        ShipmentResponse shipment = shipmentService.create(shipReq);
        assertEquals("PREPARING", shipment.status());
        assertFalse(shipment.items().isEmpty(), "ShipmentItem mapping must be populated");
        assertEquals(100, shipment.items().getFirst().quantity());
        assertEquals(batchId, shipment.items().getFirst().batchId());

        // 8. Dispatch Shipment
        shipment = shipmentService.dispatch(superAdmin.getId(), transfer.id(), "IDEMP-DISP-" + testSuffix);
        assertEquals("DISPATCHED", shipment.status());
        StockTransfer dispatchedTransfer = transferRepository.findById(transfer.id()).orElseThrow();
        assertEquals(TransferStatus.DISPATCHED, dispatchedTransfer.getStatus());
        InventoryBalance balanceAfterDispatch = balanceRepository.findById(initialBalance.getId()).orElseThrow();
        assertEquals(200, balanceAfterDispatch.getAvailableQuantity());
        assertEquals(0, balanceAfterDispatch.getReservedQuantity());
        assertEquals(200, balanceAfterDispatch.getPhysicalQuantity());

        // 9. Receive Shipment at destination
        ReceiveRequest recvReq = new ReceiveRequest(regionalBin.getId(), List.of(new ReceiveRequest.Item(batchId, 100, 0)), "Received in good condition");
        Map<String, String> receiveResult = shipmentService.receive(superAdmin.getId(), transfer.id(), recvReq, "IDEMP-RECV-" + testSuffix);
        assertEquals("COMPLETED", receiveResult.get("status"));

        StockTransfer completedTransfer = transferRepository.findById(transfer.id()).orElseThrow();
        assertEquals(TransferStatus.COMPLETED, completedTransfer.getStatus());
        Shipment deliveredShipment = shipmentRepository.findById(shipment.id()).orElseThrow();
        assertEquals(ShipmentStatus.DELIVERED, deliveredShipment.getStatus());

        InventoryBalance destBalance = balanceRepository.findByWarehouse_IdAndBatch_Id(regionalWarehouse.getId(), batchId).orElseThrow();
        assertEquals(100, destBalance.getAvailableQuantity());
        assertEquals(100, destBalance.getPhysicalQuantity());

        // Take snapshot before duplicate receive attempt
        long initialJournalCount = journalRepository.count();
        long initialLedgerLineCount = ledgerLineRepository.count();

        // 10. AC-04 Verification: Attempt duplicate receipt without idempotency key
        UUID currentTransferId = transfer.id();
        ConflictException ex = assertThrows(ConflictException.class, () ->
            shipmentService.receive(superAdmin.getId(), currentTransferId, recvReq, null)
        );
        assertEquals("SHIPMENT_ALREADY_RECEIVED", ex.getCode());

        // CRITICAL AC-04 ASSERTIONS: No state, inventory, or journal mutation occurred
        assertEquals(initialJournalCount, journalRepository.count(), "Journal count must remain strictly unchanged on duplicate receive");
        assertEquals(initialLedgerLineCount, ledgerLineRepository.count(), "Ledger line count must remain strictly unchanged");

        InventoryBalance destBalanceAfter = balanceRepository.findByWarehouse_IdAndBatch_Id(regionalWarehouse.getId(), batchId).orElseThrow();
        assertEquals(100, destBalanceAfter.getAvailableQuantity(), "Destination available balance must not be duplicated");

        StockTransfer transferAfter = transferRepository.findById(transfer.id()).orElseThrow();
        assertEquals(TransferStatus.COMPLETED, transferAfter.getStatus(), "Transfer status must remain COMPLETED");

        Shipment shipmentAfter = shipmentRepository.findById(shipment.id()).orElseThrow();
        assertEquals(ShipmentStatus.DELIVERED, shipmentAfter.getStatus(), "Shipment status must remain DELIVERED");
    }

    @Test
    void testConcurrentAc04DuplicateReceiveProtection() throws Exception {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        Medicine medicine = medicineRepository.save(new Medicine(
            String.format("MED-CONC-%05d", new Random().nextInt(90000) + 10000), "Amoxicillin ConcRecv", "Amoxil", category, "TABLET", "500mg", "BOX", "ROOM_TEMP", 10, 90, "ACTIVE"
        ));

        // Inbound 100 units
        String batchNum = "BAT-CONCRECV-" + testSuffix;
        inboundService.receive(superAdmin.getId(), "IDEMP-INB-" + testSuffix, new InboundReceiptRequest(
            supplier.getId(), centralWarehouse.getId(), centralBin.getId(), medicine.getId(),
            batchNum, LocalDate.now().minusDays(30), LocalDate.now().plusDays(200), 100
        ));

        InventoryBalance initialBalance = balanceRepository.findByWarehouse_Id(centralWarehouse.getId(), org.springframework.data.domain.Pageable.unpaged())
            .stream().filter(b -> b.getBatch().getBatchNumber().equals(batchNum)).findFirst().orElseThrow();
        UUID batchId = initialBalance.getBatch().getId();

        // Create, approve, allocate, pick, pack, ship, dispatch
        TransferRequest req = new TransferRequest(regionalWarehouse.getId(), List.of(new TransferRequest.Item(medicine.getId(), 50)), "Conc transfer");
        TransferResponse transfer = transferService.create(superAdmin.getId(), "IDEMP-TRF-" + testSuffix, req);
        transfer = transferService.approve(superAdmin.getId(), transfer.id());
        transfer = transferService.allocate(superAdmin.getId(), transfer.id(), "IDEMP-ALLOC-" + testSuffix);
        transfer = transferService.pick(superAdmin.getId(), transfer.id(), new PickRequest(List.of(new PickRequest.Item(batchId, 50))));
        transfer = transferService.pack(transfer.id());

        ShipmentRequest shipReq = new ShipmentRequest(transfer.id(), "Carrier", "TRK-CONC-" + testSuffix, "Driver", "+111", "V-1", Instant.now().plusSeconds(86400));
        shipmentService.create(shipReq);
        shipmentService.dispatch(superAdmin.getId(), transfer.id(), "IDEMP-DISP-" + testSuffix);

        // Concurrently attempt to receive the same shipment across 10 threads
        ReceiveRequest recvReq = new ReceiveRequest(regionalBin.getId(), List.of(new ReceiveRequest.Item(batchId, 50, 0)), "Conc recv");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> futures = new ArrayList<>();
        final UUID finalTransferId = transfer.id();

        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                try {
                    shipmentService.receive(superAdmin.getId(), finalTransferId, recvReq, null);
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            }));
        }

        int successes = 0;
        int conflicts = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) successes++;
            else conflicts++;
        }
        pool.shutdown();

        assertEquals(1, successes, "Exactly one concurrent receive request must succeed");
        assertEquals(9, conflicts, "Exactly 9 concurrent receive requests must be rejected with 409 Conflict");

        InventoryBalance destBalance = balanceRepository.findByWarehouse_IdAndBatch_Id(regionalWarehouse.getId(), batchId).orElseThrow();
        assertEquals(50, destBalance.getAvailableQuantity(), "Destination quantity must equal exact dispatched amount of 50");
    }

    @Test
    void testTransferCancellationAndExactReservationRelease() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        Medicine medicine = medicineRepository.save(new Medicine(
            String.format("MED-CANC-%05d", new Random().nextInt(90000) + 10000), "Amoxicillin Cancel", "Amoxil", category, "TABLET", "500mg", "BOX", "ROOM_TEMP", 10, 90, "ACTIVE"
        ));

        // Inbound 300 units
        String batchNum = "BAT-CANCEL-" + testSuffix;
        inboundService.receive(superAdmin.getId(), "IDEMP-INB-" + testSuffix, new InboundReceiptRequest(
            supplier.getId(), centralWarehouse.getId(), centralBin.getId(), medicine.getId(),
            batchNum, LocalDate.now().minusDays(30), LocalDate.now().plusDays(200), 300
        ));

        InventoryBalance initialBalance = balanceRepository.findByWarehouse_Id(centralWarehouse.getId(), org.springframework.data.domain.Pageable.unpaged())
            .stream().filter(b -> b.getBatch().getBatchNumber().equals(batchNum)).findFirst().orElseThrow();

        // Create and allocate 200 units
        TransferRequest req = new TransferRequest(regionalWarehouse.getId(), List.of(new TransferRequest.Item(medicine.getId(), 200)), "Cancel test");
        TransferResponse transfer = transferService.create(superAdmin.getId(), "IDEMP-TRF-" + testSuffix, req);
        transfer = transferService.approve(superAdmin.getId(), transfer.id());
        transfer = transferService.allocate(superAdmin.getId(), transfer.id(), "IDEMP-ALLOC-" + testSuffix);

        InventoryBalance balanceAfterAlloc = balanceRepository.findById(initialBalance.getId()).orElseThrow();
        assertEquals(100, balanceAfterAlloc.getAvailableQuantity());
        assertEquals(200, balanceAfterAlloc.getReservedQuantity());

        // Cancel transfer
        TransferResponse cancelledTransfer = transferService.cancel(superAdmin.getId(), transfer.id(), "Order cancelled by store", "IDEMP-CANCEL-" + testSuffix);
        assertEquals("CANCELLED", cancelledTransfer.status());

        // Assert exact reservation release
        InventoryBalance balanceAfterCancel = balanceRepository.findById(initialBalance.getId()).orElseThrow();
        assertEquals(300, balanceAfterCancel.getAvailableQuantity(), "Available balance must be fully restored to 300");
        assertEquals(0, balanceAfterCancel.getReservedQuantity(), "Reserved balance must be restored to 0");
        assertEquals(300, balanceAfterCancel.getPhysicalQuantity(), "Physical balance must remain 300");
    }

    @Test
    void testIdempotencyReplayOnTransferMutations() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        Medicine medicine = medicineRepository.save(new Medicine(
            String.format("MED-IDEM-%05d", new Random().nextInt(90000) + 10000), "Amoxicillin Idemp", "Amoxil", category, "TABLET", "500mg", "BOX", "ROOM_TEMP", 10, 90, "ACTIVE"
        ));

        // 1. Inbound with Idempotency Key
        String idempKey = "IDEMP-REPLAY-KEY-" + testSuffix;
        InboundReceiptRequest inbReq = new InboundReceiptRequest(
            supplier.getId(), centralWarehouse.getId(), centralBin.getId(), medicine.getId(),
            "BAT-IDEMP-" + testSuffix, LocalDate.now().minusDays(30), LocalDate.now().plusDays(200), 100
        );
        var inb1 = inboundService.receive(superAdmin.getId(), idempKey, inbReq);
        var inb2 = inboundService.receive(superAdmin.getId(), idempKey, inbReq);
        assertEquals(inb1.journalEntryNumber(), inb2.journalEntryNumber(), "Replay must return identical cached journal entry");

        // 2. Modified request with same key -> ConflictException
        InboundReceiptRequest badInbReq = new InboundReceiptRequest(
            supplier.getId(), centralWarehouse.getId(), centralBin.getId(), medicine.getId(),
            "BAT-IDEMP-DIFF-" + testSuffix, LocalDate.now().minusDays(30), LocalDate.now().plusDays(200), 500
        );
        ConflictException ex = assertThrows(ConflictException.class, () ->
            inboundService.receive(superAdmin.getId(), idempKey, badInbReq)
        );
        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getCode());
    }
}