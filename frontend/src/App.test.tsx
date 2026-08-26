import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import App from "./App";
import { useUiStore } from "./store/uiStore";

describe("MedTrack Frontend App", () => {
  it("renders login page when unauthenticated", () => {
    useUiStore.setState({ user: null, accessToken: null });
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <App />
      </MemoryRouter>
    );
    expect(screen.getAllByText(/MedTrack/i).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /sign in/i })).toBeDefined();
  });

  it("renders app shell when authenticated", () => {
    useUiStore.setState({
      user: {
        id: "user-1",
        email: "admin@medtrack.local",
        fullName: "Super Admin",
        role: "SUPER_ADMIN",
        assignedWarehouseId: null
      },
      accessToken: "fake-jwt-token"
    });

    render(
      <MemoryRouter initialEntries={["/app/dashboard"]}>
        <App />
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: /Operations dashboard/i })).toBeDefined();
    expect(screen.getByText(/Sign out/i)).toBeDefined();
  });
});