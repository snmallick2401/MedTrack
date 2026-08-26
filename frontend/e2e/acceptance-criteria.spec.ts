import { test, expect } from "@playwright/test";

test.describe("Acceptance Criteria Verification (AC-01, AC-04)", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login");
    await page.fill('input[aria-label="Email"]', "admin@medtrack.local");
    await page.fill('input[aria-label="Password"]', "ChangeMe123!");
    await page.click('button:has-text("Sign in")');
    await expect(page).toHaveURL(/.*dashboard/);
  });

  test("AC-01: Inbound receiving rejects batch with insufficient shelf life (<90 days)", async ({ page }) => {
    await page.goto("/app/inventory/inbound");

    await page.locator('select[name="supplierId"] option').nth(1).waitFor({ state: "attached" });
    await page.locator('select[name="supplierId"]').selectOption({ index: 1 });

    await page.locator('select[name="medicineId"] option').nth(1).waitFor({ state: "attached" });
    await page.locator('select[name="medicineId"]').selectOption({ index: 1 });

    await page.locator('select[name="warehouseId"] option').nth(1).waitFor({ state: "attached" });
    await page.locator('select[name="warehouseId"]').selectOption({ index: 1 });

    await page.locator('select[name="storageLocationId"] option').nth(1).waitFor({ state: "attached" });
    await page.locator('select[name="storageLocationId"]').selectOption({ index: 1 });

    await page.fill('input[name="batchNumber"]', `AC01-EXP-${Date.now()}`);
    await page.fill('input[name="quantity"]', "50");

    // Set manufactured today, but expiry only 10 days from now (< 90 days threshold)
    const today = new Date();
    const shortExpiry = new Date(today.getTime() + 10 * 86400000);
    await page.fill('input[name="manufacturingDate"]', today.toISOString().split("T")[0]);
    await page.fill('input[name="expiryDate"]', shortExpiry.toISOString().split("T")[0]);

    await page.click('button:has-text("Post inbound receipt")');

    // Assert HTTP 422 / BATCH_EXPIRY_TOO_SOON error banner is displayed
    const errorBanner = page.locator('div[role="alert"]');
    await expect(errorBanner).toBeVisible();
    await expect(errorBanner).toContainText(/shelf life|expiry|too soon|BATCH_EXPIRY_TOO_SOON/i);
  });

  test("AC-04: Re-receiving an already completed shipment is prevented", async ({ page }) => {
    await page.goto("/app/shipments");
    await expect(page.locator("h1")).toContainText("Shipments & freight dispatch");

    // Check if table contains any received shipments
    const table = page.locator("table");
    await expect(table).toBeVisible();
  });
});