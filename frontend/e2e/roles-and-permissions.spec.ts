import { test, expect } from "@playwright/test";

test.describe("Role-Based Access Control (RBAC)", () => {
  test("SUPER_ADMIN has full operational access", async ({ page }) => {
    await page.goto("/login");
    await page.fill('input[aria-label="Email"]', "admin@medtrack.local");
    await page.fill('input[aria-label="Password"]', "ChangeMe123!");
    await page.click('button:has-text("Sign in")');

    await expect(page).toHaveURL(/.*dashboard/);
    await expect(page.getByText('SUPER_ADMIN', { exact: true }).first()).toBeVisible();

    // Can access all primary navigation areas
    await page.goto("/app/inventory");
    await expect(page.locator("h1")).toContainText("Inventory balances");

    await page.goto("/app/transfers");
    await expect(page.locator("h1")).toContainText("Stock transfers");

    await page.goto("/app/shipments");
    await expect(page.locator("h1")).toContainText("Shipments");

    await page.goto("/app/audit");
    await expect(page.locator("h1")).toContainText("Audit log");
  });
});