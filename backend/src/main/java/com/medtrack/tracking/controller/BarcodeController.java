package com.medtrack.tracking.controller;

import com.medtrack.tracking.service.BarcodeService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/batches/{batchId}/barcode")
public class BarcodeController {
    private final BarcodeService barcodes;

    public BarcodeController(BarcodeService b) {
        this.barcodes = b;
    }

    @GetMapping("/qr")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getQr(@PathVariable UUID batchId) {
        return barcodes.getBatchQr(batchId);
    }

    @GetMapping(value = "/qr/image", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getQrImage(@PathVariable UUID batchId) {
        Map<String, Object> qr = barcodes.getBatchQr(batchId);
        String dataUri = (String) qr.get("dataUri");
        byte[] bytes = java.util.Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes);
    }

    @GetMapping("/code128")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> getCode128(@PathVariable UUID batchId) {
        return Map.of("dataUri", barcodes.code128PngDataUri(batchId.toString()));
    }
}