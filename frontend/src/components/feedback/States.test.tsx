import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Loading, Empty, ErrorBanner } from "./States";

describe("Feedback States", () => {
  it("renders loading state with aria-live", () => {
    render(<Loading />);
    expect(screen.getByText(/Loading operational data/i)).toBeDefined();
  });

  it("renders empty state with custom title", () => {
    render(<Empty title="No shipments recorded" />);
    expect(screen.getByText("No shipments recorded")).toBeDefined();
  });

  it("renders error banner with alert role", () => {
    render(<ErrorBanner message="Failed to authenticate" />);
    const alert = screen.getByRole("alert");
    expect(alert.textContent).toContain("Failed to authenticate");
  });
});