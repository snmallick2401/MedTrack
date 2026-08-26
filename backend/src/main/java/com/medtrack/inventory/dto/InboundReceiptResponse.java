package com.medtrack.inventory.dto; import java.util.UUID; public record InboundReceiptResponse(UUID batchId,UUID inventoryBalanceId,String journalEntryNumber,int availableQuantity){}
