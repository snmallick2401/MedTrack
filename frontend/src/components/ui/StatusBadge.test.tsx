import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  it("renders active/in-stock success badge", () => {
    render(<StatusBadge status="ACTIVE" />);
    expect(screen.getByText("ACTIVE")).toBeDefined();
  });

  it("renders warning badge for near expiry or reserved", () => {
    render(<StatusBadge status="NEAR_EXPIRY" />);
    expect(screen.getByText("NEAR EXPIRY")).toBeDefined();
  });

  it("renders danger badge for expired or out of stock", () => {
    render(<StatusBadge status="EXPIRED" />);
    expect(screen.getByText("EXPIRED")).toBeDefined();
  });
});