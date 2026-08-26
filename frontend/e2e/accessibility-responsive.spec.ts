import { test, expect } from "@playwright/test";

test.describe("Accessibility, Responsive Viewports & Dark Mode", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login");
    await page.fill('input[aria-label="Email"]', "admin@medtrack.local");
    await page.fill('input[aria-label="Password"]', "ChangeMe123!");
    await page.click('button:has-text("Sign in")');
    await expect(page).toHaveURL(/.*dashboard/);
  });

  test("toggles dark mode seamlessly across pages", async ({ page }) => {
    const toggleButton = page.locator('button[aria-label*="mode"]');
    await expect(toggleButton).toBeVisible();

    // Toggle dark mode
    await toggleButton.click();
    const html = page.locator("html");
    await expect(html).toHaveAttribute("data-theme", "dark");

    // Navigate to Inventory page and verify dark theme persists
    await page.goto("/app/inventory");
    await expect(html).toHaveAttribute("data-theme", "dark");

    // Toggle back to light mode
    await toggleButton.click();
    await expect(html).toHaveAttribute("data-theme", "light");
  });

  test("opens and navigates command palette using keyboard shortcuts", async ({ page }) => {
    const openButton = page.locator('button[aria-label="Open command palette"]');
    if (await openButton.isVisible()) {
      await openButton.click();
    } else {
      await page.keyboard.press("Control+k");
    }
    const dialog = page.locator('div[role="dialog"][aria-label="Command palette"]');
    await expect(dialog).toBeVisible();

    // Type 'reports' in palette search
    await page.keyboard.type("reports");
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/.*reports/);
    await expect(page.locator("h1")).toContainText("Operational reports");
  });

  test("renders responsively on mobile viewport (375x812)", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto("/app/dashboard");

    // Hamburger button should be visible
    const menuButton = page.locator('button[aria-label="Toggle navigation"]');
    await expect(menuButton).toBeVisible();

    // Open sidebar drawer
    await menuButton.click();
    const nav = page.locator('nav[aria-label="Primary navigation"]');
    await expect(nav).toBeVisible();

    // Close sidebar drawer via backdrop
    const backdrop = page.locator('button[aria-label="Close navigation"]');
    await expect(backdrop).toBeVisible();
    await backdrop.click({ force: true });
  });

  test("renders responsively on tablet viewport (768x1024)", async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto("/app/dashboard");
    await expect(page.locator("h1")).toContainText("Operations dashboard");
  });
});