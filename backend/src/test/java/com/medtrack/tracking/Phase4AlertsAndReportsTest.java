package com.medtrack.tracking;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.batch.entity.*;
import com.medtrack.batch.repository.BatchRepository;
import com.medtrack.inventory.dto.InboundReceiptRequest;
import com.medtrack.inventory.entity.*;
import com.medtrack.inventory.repository.*;
import com.medtrack.inventory.service.InboundService;
import com.medtrack.medicine.entity.*;
import com.medtrack.medicine.repository.*;
import com.medtrack.notification.entity.Notification;
import com.medtrack.notification.repository.NotificationRepository;
import com.medtrack.notification.service.NotificationService;
import com.medtrack.report.service.ReportService;
import com.medtrack.shipment.dto.ShipmentRequest;
import com.medtrack.shipment.dto.ShipmentResponse;
import com.medtrack.shipment.entity.Shipment;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.shipment.service.ShipmentService;
import com.medtrack.supplier.entity.Supplier;
import com.medtrack.supplier.repository.SupplierRepository;
import com.medtrack.tracking.service.BarcodeService;
import com.medtrack.transfer.dto.PickRequest;
import com.medtrack.transfer.dto.TransferRequest;
import com.medtrack.transfer.dto.TransferResponse;
import com.medtrack.transfer.service.TransferService;
import com.medtrack.user.entity.User;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.entity.*;
import com.medtrack.warehouse.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class Phase4AlertsAndReportsTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ReportService reportService;
    @Autowired private BarcodeService barcodeService;
    @Autowired private InboundService inboundService;
    @Autowired private TransferService transferService;
    @Autowired private ShipmentService shipmentService;
    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private MedicineRepository medicineRepository;
    @Autowired private MedicineCategoryRepository categoryRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private ObjectMapper objectMapper;

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
                        warehouseRepository.save(new Warehouse("RW-TEST-P4", "Regional Test P4", WarehouseType.DISTRIBUTION_STORE, "200 Market St", null, null, "+2000", WarehouseStatus.ACTIVE))
                );

        centralBin = storageLocationRepository.findByWarehouse_Id(centralWarehouse.getId()).stream().findFirst().orElseThrow();
        regionalBin = storageLocationRepository.findByWarehouse_Id(regionalWarehouse.getId()).stream().findFirst().orElseGet(() ->
                storageLocationRepository.save(new StorageLocation(regionalWarehouse, "ZONE-R4", "RACK-01", "SHELF-01", "BIN-RW-P4"))
        );

        supplier = supplierRepository.findAll().stream().findFirst().orElseThrow();
        category = categoryRepository.findAll().stream().findFirst().orElseThrow();
    }

    @Test
    void testExpiryAlertEngineAndDeduplication() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        Medicine medicine = medicineRepository.save(new Medicine(
                String.format("MED-EXP-%05d", new Random().nextInt(90000) + 10000),
                "Expiry Alert Drug", "ExpAlert", category, "TABLET", "250mg", "BOX", "ROOM_TEMP", 5, 0, "ACTIVE"
        ));

        // Create critical batch (expires in 20 days) directly in DB to simulate stock nearing expiry
        Batch critBatch = batchRepository.save(new Batch(
                "BAT-CRIT-" + testSuffix, medicine, supplier,
                LocalDate.now().minusDays(100), LocalDate.now().plusDays(20), 50, BatchStatus.ACTIVE
        ));
        InventoryBalance critBal = new InventoryBalance(centralWarehouse, critBatch, centralBin);
        critBal.receive(50);
        balanceRepository.save(critBal);

        // Create near-expiry batch (expires in 60 days)
        Batch nearBatch = batchRepository.save(new Batch(
                "BAT-NEAR-" + testSuffix, medicine, supplier,
                LocalDate.now().minusDays(100), LocalDate.now().plusDays(60), 100, BatchStatus.ACTIVE
        ));
        InventoryBalance nearBal = new InventoryBalance(centralWarehouse, nearBatch, centralBin);
        nearBal.receive(100);
        balanceRepository.save(nearBal);

        // First evaluation: must generate alerts
        int created = notificationService.evaluateAlerts();
        assertTrue(created >= 2, "Expected at least 2 notifications for critical and near expiry batches");

        List<Notification> unread = notificationRepository.findByReadAtIsNullOrderByCreatedAtDesc();
        boolean foundCritical = unread.stream().anyMatch(n -> "EXPIRY_CRITICAL".equals(n.getType()) && n.getEntityId().equals(critBatch.getId()));
        boolean foundNear = unread.stream().anyMatch(n -> "NEAR_EXPIRY".equals(n.getType()) && n.getEntityId().equals(nearBatch.getId()));

        assertTrue(foundCritical, "EXPIRY_CRITICAL notification must be present for 20-day expiry");
        assertTrue(foundNear, "NEAR_EXPIRY notification must be present for 60-day expiry");

        // Second evaluation immediately: must deduplicate and NOT generate duplicates
        long countBeforeSecondEval = notificationRepository.count();
        notificationService.evaluateAlerts();
        long countAfterSecondEval = notificationRepository.count();
        assertEquals(countBeforeSecondEval, countAfterSecondEval, "Duplicate notifications must be prevented within deduplication window");
    }

    @Test
    void testShipmentDelayAlertEngineAndDeduplication() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        Medicine medicine = medicineRepository.save(new Medicine(
                String.format("MED-SHIP-%05d", new Random().nextInt(90000) + 10000),
                "Shipment Delay Drug", "ShipDelay", category, "TABLET", "500mg", "BOX", "ROOM_TEMP", 5, 0, "ACTIVE"
        ));

        String batchNum = "BAT-SHIP-" + testSuffix;
        inboundService.receive(superAdmin.getId(), "IDEMP-INB-" + testSuffix, new InboundReceiptRequest(
                supplier.getId(), centralWarehouse.getId(), centralBin.getId(), medicine.getId(),
                batchNum, LocalDate.now().minusDays(30), LocalDate.now().plusDays(300), 100
        ));

        InventoryBalance initialBalance = balanceRepository.findByWarehouse_Id(centralWarehouse.getId(), org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(b -> b.getBatch().getBatchNumber().equals(batchNum)).findFirst().orElseThrow();
        UUID batchId = initialBalance.getBatch().getId();

        // Create, approve, allocate, pick, pack transfer
        TransferRequest trfReq = new TransferRequest(regionalWarehouse.getId(), List.of(new TransferRequest.Item(medicine.getId(), 40)), "Delay test transfer");
        TransferResponse trf = transferService.create(superAdmin.getId(), "IDEMP-TRF-" + testSuffix, trfReq);
        transferService.approve(superAdmin.getId(), trf.id());
        transferService.allocate(superAdmin.getId(), trf.id(), "IDEMP-ALLOC-" + testSuffix);
        transferService.pick(superAdmin.getId(), trf.id(), new PickRequest(List.of(new PickRequest.Item(batchId, 40))));
        transferService.pack(trf.id());

        // Create shipment with estimatedArrival in the past (2 hours ago)
        Instant pastEta = Instant.now().minusSeconds(7200);
        ShipmentRequest shipReq = new ShipmentRequest(
                trf.id(), "Express Logistics", "TRK-DELAY-" + testSuffix, "Bob Transporter", "+1555123456", "TRUCK-99", pastEta
        );
        ShipmentResponse ship = shipmentService.create(shipReq);
        shipmentService.dispatch(superAdmin.getId(), trf.id(), "IDEMP-DISP-" + testSuffix);

        // Run alert evaluation
        notificationService.evaluateAlerts();

        List<Notification> unread = notificationRepository.findByReadAtIsNullOrderByCreatedAtDesc();
        boolean foundDelayAlert = unread.stream().anyMatch(n ->
                "SHIPMENT_DELAY".equals(n.getType()) && n.getEntityId().equals(ship.id())
        );
        assertTrue(foundDelayAlert, "SHIPMENT_DELAY notification must be generated when ETA is in the past");

        // Re-evaluate: assert deduplication
        long countBefore = notificationRepository.count();
        notificationService.evaluateAlerts();
        assertEquals(countBefore, notificationRepository.count(), "Shipment delay notification must not be duplicated");
    }

    @Test
    void testQrCodeGenerationAndDecodingContract() throws Exception {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        String sku = String.format("MED-QRCO-%05d", new Random().nextInt(90000) + 10000);
        Medicine medicine = medicineRepository.save(new Medicine(
                sku, "QR Code Test Medicine", "QRMed", category, "TABLET", "100mg", "BOX", "ROOM_TEMP", 5, 0, "ACTIVE"
        ));

        String batchNumber = "BAT-QR-" + testSuffix;
        LocalDate expiryDate = LocalDate.now().plusDays(180);
        Batch batch = batchRepository.save(new Batch(
                batchNumber, medicine, supplier, LocalDate.now().minusDays(10), expiryDate, 100, BatchStatus.ACTIVE
        ));

        // 1. Generate QR via BarcodeService
        Map<String, Object> qrResult = barcodeService.getBatchQr(batch.getId());
        assertNotNull(qrResult.get("dataUri"), "dataUri must be present");
        String dataUri = (String) qrResult.get("dataUri");
        assertTrue(dataUri.startsWith("data:image/png;base64,"), "dataUri must have PNG base64 format");

        // 2. Decode raw image bytes using ZXing reader
        byte[] pngBytes = Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()));
        String decodedRawPayload = barcodeService.decodeQr(pngBytes);
        assertNotNull(decodedRawPayload, "Decoded text from QR must not be null");

        // 3. Parse decoded JSON contract: {"sku":"...","bat":"...","exp":"..."}
        Map<?, ?> parsed = objectMapper.readValue(decodedRawPayload, Map.class);
        assertEquals(sku, parsed.get("sku"), "Decoded SKU must match medicine SKU");
        assertEquals(batchNumber, parsed.get("bat"), "Decoded batch number must match");
        assertEquals(expiryDate.toString(), parsed.get("exp"), "Decoded expiry date must match");
    }

    @Test
    void testExpiryReportAndCsvExport() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        String sku = String.format("MED-REPT-%05d", new Random().nextInt(90000) + 10000);
        Medicine medicine = medicineRepository.save(new Medicine(
                sku, "Report Verification Drug", "ReptMed", category, "TABLET", "200mg", "BOX", "ROOM_TEMP", 5, 0, "ACTIVE"
        ));

        Batch batch = batchRepository.save(new Batch(
                "BAT-REPT-" + testSuffix, medicine, supplier,
                LocalDate.now().minusDays(60), LocalDate.now().plusDays(45), 75, BatchStatus.ACTIVE
        ));
        InventoryBalance bal = new InventoryBalance(centralWarehouse, batch, centralBin);
        bal.receive(75);
        balanceRepository.save(bal);

        // 1. Expiry CSV export
        byte[] csvBytes = reportService.expiryReportCsv(90);
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("warehouseCode,warehouseName,medicineSku,genericName,batchNumber"), "CSV header must be present");
        assertTrue(csv.contains(sku), "CSV must contain medicine SKU");
        assertTrue(csv.contains("BAT-REPT-" + testSuffix), "CSV must contain batch number");

        // 2. Expiry JSON data
        List<Map<String, Object>> data = reportService.expiryReportData(90);
        assertFalse(data.isEmpty(), "Expiry data must not be empty");
        boolean foundBatch = data.stream().anyMatch(d -> ("BAT-REPT-" + testSuffix).equals(d.get("batchNumber")));
        assertTrue(foundBatch, "Batch must appear in expiry data");

        // 3. Inventory CSV export
        byte[] invCsvBytes = reportService.inventoryCsv();
        String invCsv = new String(invCsvBytes, StandardCharsets.UTF_8);
        assertTrue(invCsv.startsWith("warehouseCode,warehouseName,medicineSku"), "Inventory CSV header must be present");
        assertTrue(invCsv.contains(sku), "Inventory CSV must contain medicine SKU");
    }

    @Test
    void testNotificationReadWorkflow() {
        Notification notif = notificationRepository.save(new Notification(
                centralWarehouse, "TEST_TYPE", "Test Title", "Test Message", "BATCH", UUID.randomUUID()
        ));
        assertNull(notif.getReadAt(), "Notification should initially be unread");

        notificationService.markRead(notif.getId());

        Notification updated = notificationRepository.findById(notif.getId()).orElseThrow();
        assertNotNull(updated.getReadAt(), "Notification must have readAt timestamp after markRead()");
    }
}