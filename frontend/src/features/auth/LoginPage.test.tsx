import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "./LoginPage";
import { authApi } from "../../services/authApi";

vi.mock("../../services/authApi", () => ({
  authApi: {
    login: vi.fn()
  }
}));

describe("LoginPage", () => {
  it("renders email and password inputs and sign-in button", () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );
    expect(screen.getByLabelText(/Email/i)).toBeDefined();
    expect(screen.getByLabelText(/Password/i)).toBeDefined();
    expect(screen.getByRole("button", { name: /Sign in/i })).toBeDefined();
  });

  it("handles form submission error gracefully", async () => {
    vi.mocked(authApi.login).mockRejectedValueOnce({
      response: { data: { detail: "Invalid credentials supplied" } }
    });

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/Email/i), { target: { value: "bad@example.com" } });
    fireEvent.change(screen.getByLabelText(/Password/i), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /Sign in/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeDefined();
      expect(screen.getByText("Invalid credentials supplied")).toBeDefined();
    });
  });
});