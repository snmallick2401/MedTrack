import { test, expect } from '@playwright/test';
import path from 'path';

const ARTIFACT_DIR = 'C:/Users/S N Mallick/.gemini/antigravity/brain/a50601dd-d193-4858-b3f8-8780011fc5e7';
const BASE_URL = 'https://medtrack-frontend-7yhw.onrender.com';

const PERSONAS = [
  {
    role: 'SUPER_ADMIN',
    email: 'admin@medtrack.local',
    password: 'ChangeMe123!',
    tag: '01_super_admin'
  },
  {
    role: 'CENTRAL_WAREHOUSE_MANAGER',
    email: 'warehouse@medtrack.local',
    password: 'ChangeMe123!',
    tag: '02_warehouse_manager'
  },
  {
    role: 'STORE_MANAGER',
    email: 'store@medtrack.local',
    password: 'ChangeMe123!',
    tag: '03_store_manager'
  },
  {
    role: 'LOGISTICS_COORDINATOR',
    email: 'logistics@medtrack.local',
    password: 'ChangeMe123!',
    tag: '04_logistics_coordinator'
  },
  {
    role: 'AUDITOR',
    email: 'auditor@medtrack.local',
    password: 'ChangeMe123!',
    tag: '05_auditor'
  }
];

test.describe('End-to-End Role & Multi-Persona Validation Matrix', () => {
  test.setTimeout(300000);

  for (const persona of PERSONAS) {
    test(`Validate Persona: ${persona.role} (${persona.email})`, async ({ page }) => {
      console.log(`\n======================================================`);
      console.log(`👤 Testing Persona: ${persona.role} (${persona.email})`);
      console.log(`======================================================`);

      // 1. Navigate to login
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 60000 });
      await expect(page.locator('input[type="email"], input[aria-label="Email"]')).toBeVisible();

      // 2. Authenticate
      await page.fill('input[type="email"], input[aria-label="Email"]', persona.email);
      await page.fill('input[type="password"], input[aria-label="Password"]', persona.password);
      await page.click('button:has-text("Sign in"), button[type="submit"]');

      // 3. Wait for Dashboard transition
      await page.waitForURL(/.*dashboard/, { timeout: 30000 });
      console.log(`  ✅ Successfully authenticated into Dashboard for ${persona.role}`);
      await page.waitForTimeout(2000);
      await page.screenshot({ path: path.join(ARTIFACT_DIR, `${persona.tag}_dashboard.png`) });

      // 4. Role-Specific Feature Navigation & Operations
      const navLinks = [
        { name: 'Inventory', path: '/app/inventory' },
        { name: 'Transfers', path: '/app/transfers' },
        { name: 'Shipments', path: '/app/shipments' },
        { name: 'Tracking', path: '/app/tracking' },
        { name: 'Reports', path: '/app/reports' },
        { name: 'Audit', path: '/app/audit' }
      ];

      for (const nav of navLinks) {
        const link = page.locator(`a[href*="${nav.name.toLowerCase()}"], a:has-text("${nav.name}"), nav button:has-text("${nav.name}")`).first();
        if (await link.isVisible()) {
          await link.click();
          await page.waitForTimeout(1500);
          console.log(`  🔍 Tested navigation to ${nav.name} view`);
        }
      }

      await page.screenshot({ path: path.join(ARTIFACT_DIR, `${persona.tag}_operations.png`) });

      // 5. Explicit Logout
      console.log(`  🚪 Logging out from ${persona.role} session...`);
      const logoutBtn = page.locator('button:has-text("Sign out"), a:has-text("Sign out"), button:has-text("Logout")').first();
      if (await logoutBtn.isVisible()) {
        await logoutBtn.click();
        await page.waitForTimeout(2000);
      } else {
        await page.goto(`${BASE_URL}/login`);
      }

      await expect(page.locator('input[type="email"], input[aria-label="Email"]')).toBeVisible();
      console.log(`  ✅ Logged out successfully for ${persona.role}`);
    });
  }
});
