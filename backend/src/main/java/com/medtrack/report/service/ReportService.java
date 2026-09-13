package com.medtrack.report.service;

import com.medtrack.inventory.entity.InventoryBalance;
import com.medtrack.inventory.repository.InventoryBalanceRepository;
import com.medtrack.inventory.service.InventoryAuthorizationService;
import com.medtrack.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final InventoryBalanceRepository balances;
    private final InventoryAuthorizationService authorizationService;

    public ReportService(InventoryBalanceRepository b, InventoryAuthorizationService auth) {
        this.balances = b;
        this.authorizationService = auth;
    }

    @Transactional(readOnly = true)
    public byte[] inventoryCsv(User actor, UUID requestedWarehouseId) {
        UUID authorizedWarehouseId = authorizationService.resolveAuthorizedWarehouseForReport(actor, requestedWarehouseId, "INVENTORY_REPORT");
        List<InventoryBalance> balanceList = (authorizedWarehouseId != null)
                ? balances.findAllByWarehouse_Id(authorizedWarehouseId)
                : balances.findAllWithDetailsList();
        return generateInventoryCsv(balanceList);
    }

    @Transactional(readOnly = true)
    public byte[] inventoryCsv() {
        return generateInventoryCsv(balances.findAllWithDetailsList());
    }

    @Transactional(readOnly = true)
    public byte[] expiryReportCsv(User actor, Integer daysThreshold, UUID requestedWarehouseId) {
        return generateExpiryCsv(expiryReportData(actor, daysThreshold, requestedWarehouseId));
    }

    @Transactional(readOnly = true)
    public byte[] expiryReportCsv(Integer daysThreshold) {
        return generateExpiryCsv(expiryReportData(daysThreshold));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> expiryReportData(User actor, Integer daysThreshold, UUID requestedWarehouseId) {
        UUID authorizedWarehouseId = authorizationService.resolveAuthorizedWarehouseForReport(actor, requestedWarehouseId, "EXPIRY_REPORT");
        List<InventoryBalance> balanceList = (authorizedWarehouseId != null)
                ? balances.findAllByWarehouse_Id(authorizedWarehouseId)
                : balances.findAllWithDetailsList();
        return buildExpiryData(balanceList, daysThreshold);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> expiryReportData(Integer daysThreshold) {
        return buildExpiryData(balances.findAllWithDetailsList(), daysThreshold);
    }

    private byte[] generateInventoryCsv(List<InventoryBalance> balanceList) {
        StringBuilder out = new StringBuilder("warehouseCode,warehouseName,medicineSku,genericName,batchNumber,available,reserved,quarantined,physical\n");
        for (InventoryBalance b : balanceList) {
            out.append(escape(b.getWarehouse().getCode())).append(',')
               .append(escape(b.getWarehouse().getName())).append(',')
               .append(escape(b.getBatch().getMedicine().getSku())).append(',')
               .append(escape(b.getBatch().getMedicine().getGenericName())).append(',')
               .append(escape(b.getBatch().getBatchNumber())).append(',')
               .append(b.getAvailableQuantity()).append(',')
               .append(b.getReservedQuantity()).append(',')
               .append(b.getQuarantinedQuantity()).append(',')
               .append(b.getPhysicalQuantity()).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateExpiryCsv(List<Map<String, Object>> items) {
        StringBuilder out = new StringBuilder("warehouseCode,warehouseName,medicineSku,genericName,batchNumber,manufacturingDate,expiryDate,daysToExpiry,availableQuantity,reservedQuantity,physicalQuantity,status\n");
        for (Map<String, Object> item : items) {
            out.append(escape((String) item.get("warehouseCode"))).append(',')
               .append(escape((String) item.get("warehouseName"))).append(',')
               .append(escape((String) item.get("medicineSku"))).append(',')
               .append(escape((String) item.get("genericName"))).append(',')
               .append(escape((String) item.get("batchNumber"))).append(',')
               .append(item.get("manufacturingDate")).append(',')
               .append(item.get("expiryDate")).append(',')
               .append(item.get("daysToExpiry")).append(',')
               .append(item.get("availableQuantity")).append(',')
               .append(item.get("reservedQuantity")).append(',')
               .append(item.get("physicalQuantity")).append(',')
               .append(item.get("status")).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> buildExpiryData(List<InventoryBalance> balanceList, Integer daysThreshold) {
        int threshold = (daysThreshold != null && daysThreshold > 0) ? daysThreshold : 90;
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();

        for (InventoryBalance b : balanceList) {
            if (b.getPhysicalQuantity() <= 0) continue;
            LocalDate exp = b.getBatch().getExpiryDate();
            long days = ChronoUnit.DAYS.between(today, exp);

            if (days <= threshold) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("warehouseId", b.getWarehouse().getId() != null ? b.getWarehouse().getId().toString() : "");
                map.put("warehouseCode", b.getWarehouse().getCode());
                map.put("warehouseName", b.getWarehouse().getName());
                map.put("medicineId", (b.getBatch().getMedicine() != null && b.getBatch().getMedicine().getId() != null)
                        ? b.getBatch().getMedicine().getId().toString() : "");
                map.put("medicineSku", b.getBatch().getMedicine() != null ? b.getBatch().getMedicine().getSku() : "");
                map.put("genericName", b.getBatch().getMedicine() != null ? b.getBatch().getMedicine().getGenericName() : "");
                map.put("batchId", b.getBatch().getId() != null ? b.getBatch().getId().toString() : "");
                map.put("batchNumber", b.getBatch().getBatchNumber());
                map.put("manufacturingDate", b.getBatch().getManufacturingDate() != null ? b.getBatch().getManufacturingDate().toString() : "");
                map.put("expiryDate", exp.toString());
                map.put("daysToExpiry", days);
                map.put("availableQuantity", b.getAvailableQuantity());
                map.put("reservedQuantity", b.getReservedQuantity());
                map.put("quarantinedQuantity", b.getQuarantinedQuantity());
                map.put("physicalQuantity", b.getPhysicalQuantity());
                map.put("status", days <= 0 ? "EXPIRED" : (days <= 30 ? "EXPIRY_CRITICAL" : "NEAR_EXPIRY"));
                result.add(map);
            }
        }
        return result;
    }

    private String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}