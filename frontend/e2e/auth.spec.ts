import { test, expect } from "@playwright/test";

test.describe("Authentication & Session Security", () => {
  test("shows validation error on invalid login", async ({ page }) => {
    await page.goto("/login");
    await page.fill('input[aria-label="Email"]', "invalid@medtrack.local");
    await page.fill('input[aria-label="Password"]', "WrongPassword123!");
    await page.click('button:has-text("Sign in")');

    const alert = page.locator('div[role="alert"]');
    await expect(alert).toBeVisible();
  });

  test("successfully signs in as SUPER_ADMIN and signs out", async ({ page }) => {
    await page.goto("/login");
    await page.fill('input[aria-label="Email"]', "admin@medtrack.local");
    await page.fill('input[aria-label="Password"]', "ChangeMe123!");
    await page.click('button:has-text("Sign in")');

    await expect(page).toHaveURL(/.*dashboard/);
    await expect(page.locator("h1")).toContainText("Operations dashboard");

    await page.click('button:has-text("Sign out")');
    await expect(page).toHaveURL(/.*login/);
  });
});