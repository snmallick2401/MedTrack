package com.medtrack.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.medtrack.batch.entity.Batch;
import com.medtrack.batch.repository.BatchRepository;
import com.medtrack.shared.exception.NotFoundException;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class BarcodeService {
    private final BatchRepository batches;
    private final ObjectMapper json;

    public BarcodeService(BatchRepository b, ObjectMapper j) {
        this.batches = b;
        this.json = j;
    }

    public String qrPngDataUri(String payload) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPngBytes(payload));
    }

    public byte[] qrPngBytes(String payload) {
        return generateBytes(payload, BarcodeFormat.QR_CODE, 240, 240);
    }

    public String code128PngDataUri(String payload) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(code128PngBytes(payload));
    }

    public byte[] code128PngBytes(String payload) {
        return generateBytes(payload, BarcodeFormat.CODE_128, 360, 120);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> getBatchQr(UUID batchId) {
        Batch batch = batches.findById(batchId).orElseThrow(() -> new NotFoundException("Batch"));
        Map<String, String> payloadMap = Map.of(
            "sku", batch.getMedicine().getSku(),
            "bat", batch.getBatchNumber(),
            "exp", batch.getExpiryDate().toString()
        );
        String payloadJson;
        try {
            payloadJson = json.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize QR payload", e);
        }
        String dataUri = qrPngDataUri(payloadJson);
        return Map.of(
            "batchId", batch.getId().toString(),
            "payload", payloadMap,
            "dataUri", dataUri
        );
    }

    public String decodeQr(byte[] pngBytes) {
        try {
            var bufferedImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
            var source = new BufferedImageLuminanceSource(bufferedImage);
            var bitmap = new BinaryBitmap(new HybridBinarizer(source));
            var result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode QR image", e);
        }
    }

    private byte[] generateBytes(String payload, BarcodeFormat format, int width, int height) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(payload, format, width, height);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot generate barcode", e);
        }
    }
}