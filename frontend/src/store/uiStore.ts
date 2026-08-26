import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { User } from "../types/api";

type State = {
  user: User | null;
  accessToken: string | null;
  warehouseId: string | null;
  sidebarOpen: boolean;
  setSession: (u: User, t: string) => void;
  logout: () => void;
  setWarehouse: (id: string | null) => void;
  toggleSidebar: () => void;
};

export const useUiStore = create<State>()(
  persist(
    set => ({
      user: null,
      accessToken: null,
      warehouseId: null,
      sidebarOpen: false,
      setSession: (user, accessToken) =>
        set({ user, accessToken, warehouseId: user.assignedWarehouseId ?? null }),
      logout: () => set({ user: null, accessToken: null, warehouseId: null }),
      setWarehouse: warehouseId => set({ warehouseId }),
      toggleSidebar: () => set(s => ({ sidebarOpen: !s.sidebarOpen }))
    }),
    {
      name: "medtrack-auth-session"
    }
  )
);