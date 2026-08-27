import { test, expect } from "@playwright/test";

test.describe("MedTrack Primary Pharmaceutical Business Journey", () => {
  const uniqueId = Date.now().toString().slice(-5);
  const sku = `MED-AMOX-${uniqueId}`;
  const genericName = "Amoxicillin Trihydrate";
  const batchNumber = `BAT-2026-${uniqueId}`;

  test("executes end-to-end pharmaceutical workflow against live PostgreSQL backend", async ({ page }) => {
    // 1. Login
    await page.goto("/login");
    await page.fill('input[aria-label="Email"]', "admin@medtrack.local");
    await page.fill('input[aria-label="Password"]', "ChangeMe123!");
    await page.click('button:has-text("Sign in")');

    await expect(page).toHaveURL(/.*dashboard/);
    await expect(page.locator("h1")).toContainText("Operations dashboard");

    // 2. Register Medicine
    await page.goto("/app/medicines");
    await page.click('button:has-text("New medicine")');

    await page.fill('input[name="sku"]', sku);
    await page.fill('input[name="genericName"]', genericName);
    await page.fill('input[name="brandName"]', "Amoxil Standard");

    // Wait for categories to load from backend (attached in DOM)
    const catOption = page.locator('select[name="categoryId"] option').nth(1);
    await catOption.waitFor({ state: "attached" });
    await page.locator('select[name="categoryId"]').selectOption({ index: 1 });

    await page.locator('select[name="dosageForm"]').selectOption("CAPSULE");
    await page.fill('input[name="strength"]', "500mg");
    await page.fill('input[name="unitOfMeasure"]', "BOX");
    await page.fill('input[name="minStockThreshold"]', "20");
    await page.fill('input[name="minReceivingShelfLifeDays"]', "90");

    await page.click('button:has-text("Create medicine")');
    await page.waitForTimeout(1000);

    // Verify medicine listed in table
    await page.fill('input[id="medicine-search"]', sku);
    await expect(page.locator("table")).toContainText(sku);

    // 3. Inbound Stock Receiving
    await page.goto("/app/inventory/inbound");
    
    // Wait for suppliers to load
    await page.locator('select[name="supplierId"] option').nth(1).waitFor({ state: "attached" });
    await page.locator('select[name="supplierId"]').selectOption({ index: 1 });

    // Wait for medicines to load and select the newly registered one
    await page.locator('select[name="medicineId"] option').nth(1).waitFor({ state: "attached" });
    const medSelect = page.locator('select[name="medicineId"]');
    await medSelect.selectOption({ label: `${sku} — ${genericName}` });

    // Select central warehouse
    await page.locator('select[name="warehouseId"] option').nth(1).waitFor({ state: "attached" });
    await page.locator('select[name="warehouseId"]').selectOption({ index: 1 });
    await page.waitForTimeout(500);

    // Select storage location
    await page.locator('select[name="storageLocationId"] option').nth(1).waitFor({ state: "attached" });
    const storageSelect = page.locator('select[name="storageLocationId"]');
    await storageSelect.selectOption({ index: 1 });

    await page.fill('input[name="batchNumber"]', batchNumber);
    await page.fill('input[name="quantity"]', "250");

    const today = new Date();
    const expiry = new Date(today.getTime() + 365 * 86400000); // 1 year shelf life
    await page.fill('input[name="manufacturingDate"]', today.toISOString().split("T")[0]);
    await page.fill('input[name="expiryDate"]', expiry.toISOString().split("T")[0]);

    await page.click('button:has-text("Post inbound receipt")');

    // Verify receipt posted
    const receiptStatus = page.locator('div[role="status"]');
    await expect(receiptStatus).toBeVisible();
    await expect(receiptStatus).toContainText(/Receipt posted/i);

    // 4. Verify Inventory Balances
    await page.goto("/app/inventory");
    await expect(page.locator("table")).toBeVisible();
    await expect(page.locator("table tbody tr")).not.toHaveCount(0);

    // 5. Create Stock Transfer Request
    await page.goto("/app/transfers");
    await page.click('button:has-text("New transfer request")');

    // Select destination store DS01
    await page.locator('select[name="destinationWarehouseId"] option').nth(1).waitFor({ state: "attached" });
    const destSelect = page.locator('select[name="destinationWarehouseId"]');
    const optCount = await destSelect.locator("option").count();
    await destSelect.selectOption({ index: optCount - 1 });
    await page.fill('input[name="notes"]', "Priority Clinic Replenishment");

    // Select medicine item
    const transferMedSelect = page.locator('select[name="medicineId"]').last();
    await transferMedSelect.selectOption({ label: `${sku} — ${genericName}` });
    await page.fill('input[aria-label="Requested quantity"]', "100");

    await page.click('button:has-text("Submit transfer request")');
    await page.waitForTimeout(1000);

    // 6. Progress Transfer through Workbench
    // Step A: Approve
    const approveBtn = page.locator('button:has-text("Approve transfer")');
    await expect(approveBtn).toBeVisible();
    await approveBtn.click();
    await page.waitForTimeout(1000);

    // Step B: Run FEFO Allocation
    const fefoBtn = page.locator('button:has-text("Run FEFO allocation")');
    await expect(fefoBtn).toBeVisible();
    await fefoBtn.click();
    await page.waitForTimeout(1000);

    // Step C: Confirm Pick
    const pickBtn = page.locator('button:has-text("Confirm pick")');
    await expect(pickBtn).toBeVisible({ timeout: 10000 });
    await pickBtn.click();
    await page.waitForTimeout(1000);

    // Step D: Pack Transfer
    const packBtn = page.locator('button:has-text("Pack transfer")');
    await expect(packBtn).toBeVisible({ timeout: 10000 });
    await packBtn.click();
    await page.waitForTimeout(1000);

    // 7. Create Shipment Manifest
    await page.goto("/app/shipments");
    await page.click('button:has-text("Create shipment manifest")');

    await page.locator('select[name="transferId"] option').nth(1).waitFor({ state: "attached" });
    await page.locator('select[name="transferId"]').selectOption({ index: 1 });
    await page.fill('input[name="carrierName"]', "PharmaLogistics Rapid Carrier");
    await page.fill('input[name="trackingNumber"]', `TRK-${uniqueId}`);
    await page.fill('input[name="driverName"]', "Tariq Mansoor");
    await page.fill('input[name="vehicleNumber"]', "TRK-092");

    await page.click('button:has-text("Create shipment")');
    await page.waitForTimeout(1000);

    // 8. Dispatch Shipment
    const dispatchBtn = page.locator('button:has-text("Dispatch shipment")');
    await expect(dispatchBtn).toBeVisible({ timeout: 10000 });
    await dispatchBtn.click();
    await page.waitForTimeout(1000);

    // 9. Receive Shipment at Destination Store
    const destLocInput = page.locator('input[placeholder*="destination storage bin"]');
    await expect(destLocInput).toBeVisible();
    await destLocInput.fill("56285916-8034-4911-a839-9ec32165014a");

    const receiveBtn = page.locator('button:has-text("Confirm physical receipt")');
    await expect(receiveBtn).toBeVisible({ timeout: 10000 });
    await receiveBtn.click();
    await page.waitForTimeout(1500);

    // Verify receipt confirmed message
    await expect(page.locator('div[role="status"]')).toContainText(/Receipt Confirmed/i);

    // 10. Audit Log Check
    await page.goto("/app/audit");
    await expect(page.locator("table")).toBeVisible();
    await expect(page.locator("table")).toContainText("INBOUND_RECEIPT");

    // 11. Reports Check
    await page.goto("/app/reports");
    await expect(page.locator("h1")).toContainText("Operational reports");
    await expect(page.locator('button:has-text("Export inventory CSV")')).toBeVisible();
  });
});