import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "../../services/authApi";
import { useUiStore } from "../../store/uiStore";
import { errorMessage } from "../../utils/errors";
import type { Role, User } from "../../types/api";

export function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const setSession = useUiStore(s => s.setSession);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const r = await authApi.login(email.trim(), password);
      const user: User = r.user ?? {
        id: r.email,
        email: r.email,
        fullName:
          r.email === "admin@medtrack.local"
            ? "Local Development Administrator"
            : r.email.split("@")[0],
        role: r.role,
        assignedWarehouseId: null
      };
      setSession(user, r.accessToken);
      navigate("/app/dashboard");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-surface-soft p-4">
      <form
        onSubmit={submit}
        className="w-full max-w-sm rounded-lg border border-border bg-canvas p-6 shadow-sm"
      >
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-ink">MedTrack</h1>
          <p className="mt-1 text-xs text-muted">Pharmaceutical Inventory & Tracking</p>
        </div>

        {error && (
          <div role="alert" className="mb-4 rounded border border-danger/30 bg-danger-bg p-3 text-xs text-danger">
            {error}
          </div>
        )}

        <label className="mb-3 block text-sm font-medium text-ink">
          Email address
          <input
            required
            type="email"
            className="input mt-1"
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="admin@medtrack.local"
            aria-label="Email"
          />
        </label>

        <label className="mb-5 block text-sm font-medium text-ink">
          Password
          <input
            required
            type="password"
            className="input mt-1"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="••••••••"
            aria-label="Password"
          />
        </label>

        <button
          type="submit"
          disabled={loading}
          className="btn-primary w-full text-sm font-semibold disabled:opacity-50"
        >
          {loading ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}