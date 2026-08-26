# MedTrack — UI/UX Design System & Interface Specification

**Design System Version:** alpha  
**Name:** `MedTrack-design-system`  
**Status:** Approved for Implementation  
**Visual Direction:** Calm, Data-First, Trustworthy, Operationally Efficient, Desktop-First Responsive  

---

## 1. Visual Philosophy & Design Principles

MedTrack is an operational mission-critical tool for pharmaceutical warehouse supervisors, clinical pharmacists, and dispatch coordinators. 

The design follows four foundational tenets:
1. **Clarity Over Novelty (Airtable-style Density):** High data density with clean typographic hierarchy. Table rows, metadata badges, and numeric balances must be instantly legible without visual fatigue.
2. **Restrained Precision (Linear-style Polish):** Subtle 1px hairline borders, calm monochromatic surfaces, and near-black primary actions rather than saturated blue gradients.
3. **High-Stakes Error Prevention (Stripe-inspired Form Safety):** Explicit confirmation dialogs for destructive actions (quarantine, write-off), inline validation errors before submission, and clear dual-coded status indicators.
4. **Accessible Interaction Quality (Apple HIG Rigor):** Full WCAG 2.1 AA compliance, robust keyboard navigation (`Ctrl+K` command palette, arrow-key table navigation), and minimum 4.5:1 text contrast ratios.

---

## 2. Design Tokens & CSS Variable System

### 2.1 CSS Custom Properties (`src/styles/tokens.css`)
```css
:root {
  /* Core Surfaces & Canvases */
  --color-canvas: #FFFFFF;
  --color-surface-soft: #F8FAFC;
  --color-surface: #F4F6F8;
  --color-surface-strong: #E7EAEE;

  /* Hairline Borders */
  --color-border: #D9DEE5;
  --color-border-strong: #98A2B3;

  /* Typography & Ink */
  --color-ink: #181D26;
  --color-body: #343840;
  --color-muted: #667085;

  /* Brand & Primary Actions */
  --color-primary: #181D26;
  --color-primary-active: #0D1218;
  --color-on-primary: #FFFFFF;
  --color-brand-blue: #2563EB;
  --color-brand-teal: #0F766E;

  /* Semantics (Info, Success, Warning, Danger) */
  --color-info: #1D4ED8;
  --color-info-bg: #EFF6FF;
  --color-info-border: #93C5FD;

  --color-success: #15803D;
  --color-success-bg: #F0FDF4;
  --color-success-border: #86EFAC;

  --color-warning: #B45309;
  --color-warning-bg: #FFFBEB;
  --color-warning-border: #FCD34D;

  --color-danger: #B42318;
  --color-danger-bg: #FEF3F2;
  --color-danger-border: #FDA29B;
}
```

### 2.2 Tailwind CSS Configuration (`tailwind.config.js`)
All components consume token classes rather than raw hex values:
```javascript
/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        canvas: "var(--color-canvas)",
        surface: {
          soft: "var(--color-surface-soft)",
          DEFAULT: "var(--color-surface)",
          strong: "var(--color-surface-strong)",
        },
        border: {
          DEFAULT: "var(--color-border)",
          strong: "var(--color-border-strong)",
        },
        ink: "var(--color-ink)",
        body: "var(--color-body)",
        muted: "var(--color-muted)",
        primary: {
          DEFAULT: "var(--color-primary)",
          active: "var(--color-primary-active)",
          foreground: "var(--color-on-primary)",
        },
        brand: {
          blue: "var(--color-brand-blue)",
          teal: "var(--color-brand-teal)",
        },
        status: {
          info: { DEFAULT: "var(--color-info)", bg: "var(--color-info-bg)", border: "var(--color-info-border)" },
          success: { DEFAULT: "var(--color-success)", bg: "var(--color-success-bg)", border: "var(--color-success-border)" },
          warning: { DEFAULT: "var(--color-warning)", bg: "var(--color-warning-bg)", border: "var(--color-warning-border)" },
          danger: { DEFAULT: "var(--color-danger)", bg: "var(--color-danger-bg)", border: "var(--color-danger-border)" },
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "-apple-system", "sans-serif"],
      },
      borderRadius: {
        sm: "4px",
        md: "6px",
        lg: "8px",
        xl: "12px",
      },
    },
  },
  plugins: [],
};
```

---

## 3. Typography & Type Scale

**Font Stack:** `Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`

| Token | Font Size | Line Height | Weight | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **`display-xl`** | 40px (2.5rem) | 1.1 (44px) | 500 | Main dashboard KPI counters |
| **`display-lg`** | 32px (2.0rem) | 1.2 (38px) | 500 | Top-level summary metric numbers |
| **`title-lg`** | 24px (1.5rem) | 1.3 (31px) | 600 | Page headers (e.g., "Central Inventory") |
| **`title-md`** | 20px (1.25rem)| 1.4 (28px) | 600 | Modal titles, Section headers |
| **`title-sm`** | 16px (1.0rem) | 1.4 (22px) | 600 | Card titles, Table section headers |
| **`label-md`** | 14px (0.875rem)| 1.4 (20px) | 500 | Form labels, Table column headers |
| **`body-lg`** | 16px (1.0rem) | 1.5 (24px) | 400 | Lead paragraphs, modal explanatory text |
| **`body-md`** | 14px (0.875rem)| 1.5 (21px) | 400 | Standard table cells, form inputs, body text |
| **`caption`** | 12px (0.75rem)| 1.4 (17px) | 400 | Metadata, helper text, timestamps |
| **`button`** | 14px (0.875rem)| 1.4 (20px) | 500 | Button actions, segmented tabs |

---

## 4. Spacing, Sizing, & Border Radii

### 4.1 4px Base Spacing Grid
- `4px` (`space-1`): Gap between icon and text in buttons/badges.
- `8px` (`space-2`): Gap between compact list items, padding in small inputs.
- `12px` (`space-3`): Table vertical cell padding, compact card padding.
- `16px` (`space-4`): Default form row gap, standard container padding.
- `24px` (`space-6`): Card inner padding, modal content padding, table horizontal padding.
- `32px` (`space-8`): Page outer padding, major section separation.
- `48px` (`space-12`): Top-level layout spacing, dashboard metric block separation.

### 4.2 Border Radii Standards
- **`4px` (`rounded`):** Micro tags, table cell selection checkboxes.
- **`6px` (`rounded-md`):** Form inputs, dropdown selects, standard buttons.
- **`8px` (`rounded-lg`):** Dashboard cards, data table wrappers, notification items.
- **`12px` (`rounded-xl`):** Modals, slide-over drawers, major floating dialogs.
- **`9999px` (`rounded-full`):** Status pills, avatar circles, icon-only action toggles.

### 4.3 Elevation & Shadows
- **Elevation 0 (Flat):** `border: 1px solid #D9DEE5; background: #FFFFFF;` (Default for cards and tables).
- **Elevation 1 (Hover / Sub-surface):** `box-shadow: 0 1px 2px 0 rgba(16, 24, 40, 0.05);`
- **Elevation 2 (Dropdowns & Popovers):** `box-shadow: 0 4px 6px -2px rgba(16, 24, 40, 0.08), 0 12px 16px -4px rgba(16, 24, 40, 0.08);`
- **Elevation 3 (Modals & Drawers):** `box-shadow: 0 8px 8px -4px rgba(16, 24, 40, 0.04), 0 20px 24px -4px rgba(16, 24, 40, 0.10);`

---

## 5. Application Shell & Layout Architecture

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ MedTrack │ Warehouse: [ Central Depot ▼ ] │ Search (Ctrl+K)   │ [Bell (3)] [Elena R. ▼]│
├──────────┴────────────────────────────────┴───────────────────┴────────────────────────┤
│ SIDEBAR (240px) │ MAIN CONTENT AREA (Responsive, Max Width: 1440px)                    │
│                 │                                                                      │
│ ⦿ Dashboard     │ [ Page Title: Central Inventory ]          [ + Inbound Consignment ] │
│ 📦 Inventory    ├──────────────────────────────────────────────────────────────────────┤
│ 💊 Medicines    │ [ Search by SKU, Name... ] [ Category ▼ ] [ Status ▼ ] [ Export CSV ]│
│ 🏷 Batches      ├──────────────────────────────────────────────────────────────────────┤
│ 🏢 Warehouses   │ Medicine | Batch | Available | Reserved | Expiry | Status | Actions  │
│ 🚚 Shipments    │ ─────────┼───────┼───────────┼──────────┼────────┼────────┼───────── │
│ 🗺 Live Map     │ Amox 500 | B-101 | 450 boxes | 50 boxes | 12/2026| InStock| [•••]     │
│ 📄 Reports      │ Cipro 250| B-092 |  20 vials |  0 vials | 09/2026| LowStk | [•••]     │
│ 📋 Audit Logs   │                                                                      │
│                 │ [Showing 1-20 of 142 items]                      [ < 1 2 3 ... 8 > ] │
│ ⚙ Settings      │                                                                      │
└─────────────────┴──────────────────────────────────────────────────────────────────────┘
```

### 5.1 Sidebar (Left Navigation)
- Fixed left width: `240px` desktop (`w-60`). Collapsible to `64px` icon rail on tablet (`768px – 1023px`).
- Background: `#FFFFFF`, Right border: `1px solid #D9DEE5`.
- Navigation items: Height `36px`, rounded `6px`, padding `8px 12px`, text `14px / 500`.
- Active state: Background `#F4F6F8`, text `#181D26`, left border accent `3px solid #2563EB`.

### 5.2 Topbar (Header Bar)
- Height: `56px` (`h-14`), sticky top `z-30`.
- Background: `#FFFFFF` with bottom border `1px solid #D9DEE5`.
- Contains:
  1. **Warehouse Context Selector:** Dropdown to switch active store view (`Central Warehouse`, `Store North`, `Store East`).
  2. **Global Search Input:** `Ctrl+K` command search box for instant lookup of SKUs, Batches, or Shipments.
  3. **Notification Center Bell:** Shows unread badge count with popover drawer.
  4. **User Profile Dropdown:** Displays User Full Name, Role badge (`CENTRAL_WAREHOUSE_MANAGER`), and Sign Out action.

---

## 6. Detailed Component Specifications

### 6.1 Buttons
- **Primary Button:**
  - Background `#181D26`, text `#FFFFFF`, hover `#0D1218`, focus `ring-2 ring-offset-2 ring-brand-blue`.
  - Height `36px` (`px-4 py-2`), font `14px / 500`, border-radius `6px`.
- **Secondary / Outline Button:**
  - Background `#FFFFFF`, border `1px solid #D9DEE5`, text `#343840`, hover `#F8FAFC`.
- **Destructive / Danger Button:**
  - Background `#B42318`, text `#FFFFFF`, hover `#912018`.
- **Icon-Only Button:**
  - Size `36px x 36px`, rounded `6px`, centered Lucide icon (`size={18}`), hover `#F4F6F8`.
- **Loading State:** Disabled button with animated spinning spinner and `aria-busy="true"`.

### 6.2 Form Inputs & Controls
- **Text Input / Number Input / Select:**
  - Height `36px`, border `1px solid #D9DEE5`, border-radius `6px`, background `#FFFFFF`, text `#181D26`.
  - Focus state: Border `#2563EB`, box-shadow `0 0 0 2px rgba(37, 99, 235, 0.2)`.
  - Error state: Border `#B42318`, helper text in `#B42318` with AlertCircle icon (`size={14}`).
- **Combobox / Autocomplete:**
  - Asynchronous search dropdown with highlight on matching query substrings and keyboard navigation (ArrowUp/Down, Enter).

### 6.3 Data Table System & Explicit State Matrix

Data tables are the core workhorse of the MedTrack operational interface. Every table component must implement all 8 explicit UI states:

| State | Visual Trigger / Specification | CSS / Utility Implementation |
| :--- | :--- | :--- |
| **1. `default`** | Height 48px, background `#FFFFFF`, bottom border `1px solid #E7EAEE`, text `#343840`. | `h-12 bg-canvas border-b border-border text-body` |
| **2. `hover`** | Subtle neutral surface highlight on pointer hover; cursor pointer if row is clickable. | `hover:bg-surface-soft transition-colors cursor-pointer` |
| **3. `selected`** | Row checkbox checked; background `#EFF6FF`, left accent bar `3px solid #2563EB`. | `bg-status-info-bg border-l-[3px] border-l-brand-blue` |
| **4. `focused`** | Active row during keyboard navigation (Arrow Up/Down); 2px brand blue focus ring. | `outline-none ring-2 ring-inset ring-brand-blue` |
| **5. `loading`** | Skeleton placeholder rows rendered with subtle pulse shimmer animation. | `animate-pulse bg-surface-strong h-8 rounded-md` |
| **6. `empty`** | Centered illustration/icon, title "No inventory items found", description, and primary CTA. | `flex flex-col items-center justify-center py-12 text-muted` |
| **7. `error`** | Inline warning/error banner with explanation and a "Retry Query" action button. | `p-4 bg-status-danger-bg border border-status-danger-border` |
| **8. `disabled`** | Dimmed row for discontinued/locked records; opacity 0.5, cursor `not-allowed`. | `opacity-50 pointer-events-none cursor-not-allowed bg-surface` |

### 6.4 Domain Status Badges & 1-to-1 Backend Vocabulary Mapping

The frontend strictly mirrors the backend domain status enums with zero divergence. Every badge is rendered as a `rounded-full` pill with a 6px status dot, text label, and high-contrast border:

#### 1. Inventory Balance Status (`InventoryStatus`)
- **`IN_STOCK`:** Dot `#15803D` | Background `#F0FDF4` | Border `#86EFAC` | Text `#15803D`
- **`LOW_STOCK`:** Dot `#B45309` | Background `#FFFBEB` | Border `#FCD34D` | Text `#B45309`
- **`OUT_OF_STOCK`:** Dot `#B42318` | Background `#FEF3F2` | Border `#FDA29B` | Text `#B42318`

#### 2. Batch Lifecycle Status (`BatchStatus`)
- **`ACTIVE`:** Dot `#15803D` | Background `#F0FDF4` | Border `#86EFAC` | Text `#15803D`
- **`EXPIRING_SOON`:** Dot `#B45309` | Background `#FFFBEB` | Border `#FCD34D` | Text `#B45309`
- **`EXPIRED`:** Dot `#B42318` | Background `#FEF3F2` | Border `#FDA29B` | Text `#B42318`
- **`QUARANTINED`:** Dot `#7E22CE` | Background `#FAF5FF` | Border `#D8B4FE` | Text `#7E22CE`
- **`DEPLETED`:** Dot `#667085` | Background `#F4F6F8` | Border `#D9DEE5` | Text `#667085`

#### 3. Stock Transfer Lifecycle Status (`TransferStatus`)
- **`DRAFT` / `REQUESTED`:** Dot `#667085` | Background `#F8FAFC` | Border `#D9DEE5` | Text `#343840`
- **`APPROVED` / `ALLOCATED`:** Dot `#1D4ED8` | Background `#EFF6FF` | Border `#93C5FD` | Text `#1D4ED8`
- **`PICKED` / `PACKED`:** Dot `#0F766E` | Background `#F0FDFA` | Border `#99F6E4` | Text `#0F766E`
- **`DISPATCHED` / `IN_TRANSIT`:** Dot `#2563EB` | Background `#EFF6FF` | Border `#93C5FD` | Text `#2563EB`
- **`RECEIVED` / `COMPLETED`:** Dot `#15803D` | Background `#F0FDF4` | Border `#86EFAC` | Text `#15803D`
- **`DISCREPANCY_FLAGGED`:** Dot `#B45309` | Background `#FFFBEB` | Border `#FCD34D` | Text `#B45309`
- **`CANCELLED` / `REJECTED`:** Dot `#B42318` | Background `#FEF3F2` | Border `#FDA29B` | Text `#B42318`

#### 4. Shipment Transportation Status (`ShipmentStatus`)
- **`PREPARING`:** Dot `#667085` | Background `#F8FAFC` | Border `#D9DEE5` | Text `#475467`
- **`DISPATCHED`:** Dot `#1D4ED8` | Background `#EFF6FF` | Border `#93C5FD` | Text `#1D4ED8`
- **`IN_TRANSIT`:** Dot `#0F766E` | Background `#F0FDFA` | Border `#99F6E4` | Text `#0F766E`
- **`OUT_FOR_DELIVERY`:** Dot `#2563EB` | Background `#EFF6FF` | Border `#93C5FD` | Text `#2563EB`
- **`DELIVERED`:** Dot `#15803D` | Background `#F0FDF4` | Border `#86EFAC` | Text `#15803D`
- **`DELAYED` / `EXCEPTION_FAILED`:** Dot `#B42318` | Background `#FEF3F2` | Border `#FDA29B` | Text `#B42318`
- **`CANCELLED`:** Dot `#667085` | Background `#F4F6F8` | Border `#D9DEE5` | Text `#667085`

### 6.5 3-Bucket Inventory Breakdown Component
Rendered in table cells and detail panels to provide immediate, unambiguous visual clarity across physical stock allocations:
```text
┌───────────────────────────────────────────────────────────────┐
│ 450 total  [ 400 Available (Green) ] [ 50 Reserved (Amber) ] │
└───────────────────────────────────────────────────────────────┘
```
- **Available Pill:** Background `#F0FDF4`, Border `#86EFAC`, Text `#15803D` (Ready for picking).
- **Reserved Pill:** Background `#FFFBEB`, Border `#FCD34D`, Text `#B45309` (Locked for approved transfer).
- **Quarantined Pill:** Background `#FAF5FF`, Border `#D8B4FE`, Text `#7E22CE` (Quality / Inspection hold).

---

## 7. Operational Workflow UI Patterns

### 7.1 Stock Transfer 4-Step Stepper Wizard
```text
[ (1) Request Items ] ──────► [ (2) FEFO Allocation ] ──────► [ (3) Pack & Assign Carrier ] ──────► [ (4) Dispatch Confirmation ]
      COMPLETED                     ACTIVE                          UPCOMING                              UPCOMING
```
- **Step 1:** Select Destination Store, search medicines, enter requested quantities.
- **Step 2:** System automatically renders FEFO Batch Allocation table showing candidate batches sorted by expiry, batch balance, and allocated count with a secondary **"Override FEFO"** action button.
- **Step 3:** Print pick list with warehouse bin barcodes; assign carrier name, tracking number, and vehicle identifier.
- **Step 4:** Review summary manifest, click "Confirm & Dispatch", and print consolidated QR shipping label.

### 7.2 FEFO Manual Override Dialog & Warning Banner
- **Warning Banner:** Prominently styled with `#FFFBEB` background and `#B45309` warning border:  
  *"Attention: FEFO picking optimizes inventory shelf-life and prevents pharmaceutical spoilage. Overriding will be permanently stamped in the compliance audit trail."*
- **Mandatory Fields:**
  1. **Batch Selector:** Pick alternative active batch.
  2. **Override Reason Textarea (Required, min 15 chars):** E.g., *"Customer requested batch with >18 months shelf-life for export shipment."*
  3. **Supervisor Authentication Re-affirmation:** Displays current logged-in user and timestamp.
- **Submit Button:** Secondary near-black action titled *"Confirm & Record Override"*.

### 7.3 Integrated Barcode / QR Scanner Modal
- **Camera Viewport:** 320px x 320px centered square targeting overlay with green corner brackets.
- **Scanner Audio/Haptic Feedback:** Short 800Hz beep on successful QR code decode.
- **Fallback Keyboard Wedge Input:** Hidden focusable text input ready to catch 1D USB scanner data string delimited by `Enter`.

### 7.3 Live Transport Tracking Map & Timeline
```text
┌──────────────────────────────────────────────────────────┬──────────────────────────────────────────┐
│ LEAFLET ROUTE MAP (60% Width)                            │ WAYPOINT MILESTONE TIMELINE (40% Width)  │
│                                                          │                                          │
│   [ 🏢 Central Depot ]                                   │ ⦿ 09:30 AM — Departed Central Depot     │
│           \                                              │   Location: Central Warehouse, Bay 4     │
│            \ (Route Polyline: #2563EB)                   │                                          │
│             \                                            │ ⦿ 11:15 AM — Checkpoint Alpha (Passed)   │
│              ● [ 🚚 MediExpress VAN-04 ]                 │   Location: Highway 101, Mile 42         │
│               \                                          │                                          │
│                \                                         │ ○ ETA 02:30 PM — In Transit              │
│                 [ 🏥 Store North Dispensary ]            │   Location: Store North Dispensary       │
└──────────────────────────────────────────────────────────┴──────────────────────────────────────────┘
```

---

## 8. Accessibility (a11y) & Responsive Breakpoints

### 8.1 Accessibility Invariants
- **Color Contrast:** All text meets or exceeds WCAG 2.1 AA ratio ($\ge 4.5:1$ for standard text, $\ge 3:1$ for large headings).
- **Keyboard Traps & Focus Management:** Modals trap Tab focus inside dialog until dismissed; pressing `Escape` closes any open drawer/modal.
- **Screen Reader Announcements:** Dynamic state updates (e.g., "Scanning QR code...", "Transfer approved") announce via `aria-live="polite"` regions.
- **Reduced Motion:** When `prefers-reduced-motion: reduce` is enabled, all CSS transitions and loading spinners are instantaneous.

### 8.2 Responsive Breakpoints
- **Mobile (`< 640px`):** Stacked single-column layouts; sidebar transforms into off-canvas hamburger drawer; data tables convert to card lists.
- **Tablet (`640px – 1023px`):** Sidebar collapses to 64px icon rail; 2-column KPI card grid.
- **Desktop (`1024px – 1439px`):** Standard 240px sidebar; full data tables with horizontal scroll if needed; 4-column KPI grid.
- **Wide Desktop (`1440px+`):** Full 1440px container centering with generous 32px padding and side-by-side map/timeline views.
