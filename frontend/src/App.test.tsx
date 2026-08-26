import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { useUiStore } from "./store/uiStore";

function renderWithClient(ui: React.ReactElement, { route = "/" } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false
      }
    }
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        {ui}
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("MedTrack Frontend App", () => {
  it("renders login page when unauthenticated", () => {
    useUiStore.setState({ user: null, accessToken: null });
    renderWithClient(<App />, { route: "/login" });
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

    renderWithClient(<App />, { route: "/app/dashboard" });
    expect(screen.getByRole("heading", { name: /Operations dashboard/i })).toBeDefined();
    expect(screen.getByText(/Sign out/i)).toBeDefined();
  });
});