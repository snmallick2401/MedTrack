package com.medtrack.report.service;

import com.medtrack.inventory.entity.InventoryBalance;
import com.medtrack.inventory.repository.InventoryBalanceRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final InventoryBalanceRepository balances;

    public ReportService(InventoryBalanceRepository b) {
        this.balances = b;
    }

    @Transactional(readOnly = true)
    public byte[] inventoryCsv() {
        StringBuilder out = new StringBuilder("warehouseCode,warehouseName,medicineSku,genericName,batchNumber,available,reserved,quarantined,physical\n");
        for (InventoryBalance b : balances.findAll()) {
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

    @Transactional(readOnly = true)
    public byte[] expiryReportCsv(Integer daysThreshold) {
        int threshold = (daysThreshold != null && daysThreshold > 0) ? daysThreshold : 90;
        LocalDate today = LocalDate.now();
        StringBuilder out = new StringBuilder("warehouseCode,warehouseName,medicineSku,genericName,batchNumber,manufacturingDate,expiryDate,daysToExpiry,availableQuantity,reservedQuantity,physicalQuantity,status\n");

        for (Map<String, Object> item : expiryReportData(threshold)) {
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> expiryReportData(Integer daysThreshold) {
        int threshold = (daysThreshold != null && daysThreshold > 0) ? daysThreshold : 90;
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();

        for (InventoryBalance b : balances.findAll()) {
            if (b.getPhysicalQuantity() <= 0) continue;
            LocalDate exp = b.getBatch().getExpiryDate();
            long days = ChronoUnit.DAYS.between(today, exp);

            if (days <= threshold) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("warehouseId", b.getWarehouse().getId().toString());
                map.put("warehouseCode", b.getWarehouse().getCode());
                map.put("warehouseName", b.getWarehouse().getName());
                map.put("medicineId", b.getBatch().getMedicine().getId().toString());
                map.put("medicineSku", b.getBatch().getMedicine().getSku());
                map.put("genericName", b.getBatch().getMedicine().getGenericName());
                map.put("batchId", b.getBatch().getId().toString());
                map.put("batchNumber", b.getBatch().getBatchNumber());
                map.put("manufacturingDate", b.getBatch().getManufacturingDate().toString());
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