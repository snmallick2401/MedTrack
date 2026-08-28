import axios from "axios";
import { useUiStore } from "../store/uiStore";
import type { User, Role } from "../types/api";

const configuredBase = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";
const baseURL = configuredBase.replace(/\/$/, "").endsWith("/api/v1") ? configuredBase.replace(/\/$/, "") : `${configuredBase.replace(/\/$/, "")}/api/v1`;
export const api = axios.create({
  baseURL,
  withCredentials: true
});

api.interceptors.request.use(c => {
  const token = useUiStore.getState().accessToken;
  if (token) c.headers.Authorization = `Bearer ${token}`;
  return c;
});

let refreshing = false;

api.interceptors.response.use(
  r => r,
  async error => {
    const cfg = error.config;
    if (error.response?.status === 401 && !cfg?._retry && !refreshing) {
      cfg._retry = true;
      refreshing = true;
      try {
        const r = await axios.post(
          `${api.defaults.baseURL}/auth/refresh`,
          {},
          { withCredentials: true }
        );
        const refreshedUser: User = r.data.user ?? {
          id: r.data.email,
          email: r.data.email,
          fullName: r.data.email.split("@")[0],
          role: r.data.role as Role,
          assignedWarehouseId: null
        };
        useUiStore.getState().setSession(refreshedUser, r.data.accessToken);
        cfg.headers.Authorization = `Bearer ${r.data.accessToken}`;
        return api(cfg);
      } catch {
        useUiStore.getState().logout();
      } finally {
        refreshing = false;
      }
    }
    return Promise.reject(error);
  }
);
