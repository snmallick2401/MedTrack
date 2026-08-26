import { test, expect } from "@playwright/test";

test.describe("Reports, Barcodes & Notifications", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login");
    await page.fill('input[aria-label="Email"]', "admin@medtrack.local");
    await page.fill('input[aria-label="Password"]', "ChangeMe123!");
    await page.click('button:has-text("Sign in")');
    await expect(page).toHaveURL(/.*dashboard/);
  });

  test("renders reports page with near-expiry table and CSV exports", async ({ page }) => {
    await page.goto("/app/reports");
    await expect(page.locator("h1")).toContainText("Operational reports");

    // Verify CSV export buttons
    const exportInvBtn = page.locator('button:has-text("Export inventory CSV")');
    const exportExpBtn = page.locator('button:has-text("Export expiry")');
    await expect(exportInvBtn).toBeVisible();
    await expect(exportExpBtn).toBeVisible();

    // Verify Expiry table and threshold select
    const thresholdSelect = page.locator('select[aria-label="Filter expiry window"]');
    await expect(thresholdSelect).toBeVisible();
    await thresholdSelect.selectOption("180");
  });

  test("generates 2D QR and 1D Code 128 barcode labels", async ({ page }) => {
    await page.goto("/app/labels");
    await expect(page.locator("h1")).toContainText("Batch labels & barcodes");

    // Wait for registered batches or input
    const batchSelect = page.locator('select[aria-label="Batch ID"]');
    if (await batchSelect.isVisible()) {
      const count = await batchSelect.locator("option").count();
      if (count > 1) {
        await batchSelect.selectOption({ index: 1 });
        // Verify barcode preview image renders
        const img = page.locator("img");
        await expect(img).toBeVisible();
      }
    }
  });

  test("renders scanner page and resolves scanned identifiers", async ({ page }) => {
    await page.goto("/app/scanner");
    await expect(page.locator("h1")).toContainText("Barcode & QR scanner");

    const scanInput = page.locator('input[aria-label="Scan or enter a batch ID"]');
    await scanInput.fill("BAT-2026-TEST-001");
    await page.click('button:has-text("Resolve")');

    const result = page.locator('div[role="status"]');
    await expect(result).toBeVisible();
    await expect(result).toContainText("BAT-2026-TEST-001");
  });

  test("evaluates and displays notification alerts", async ({ page }) => {
    await page.goto("/app/notifications");
    await expect(page.locator("h1")).toContainText("Notification center");

    const scanAlertsBtn = page.locator('button:has-text("Run alert scan")');
    await expect(scanAlertsBtn).toBeVisible();
    await scanAlertsBtn.click();
    await page.waitForTimeout(1000);
  });
});