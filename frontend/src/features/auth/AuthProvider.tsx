import { createContext, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { AuthStatus, AuthUser, LoginPayload, RegisterPayload } from "./authTypes";

type AuthContextValue = {
  status: AuthStatus;
  user: AuthUser | null;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => void;
};

const STORAGE_KEY = "pricewatchza.session";

const AuthContext = createContext<AuthContextValue | null>(null);

function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

function persistUser(user: AuthUser) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => readStoredUser());

  const value = useMemo<AuthContextValue>(() => {
    return {
      status: user ? "authenticated" : "anonymous",
      user,
      async login(payload) {
        const nextUser: AuthUser = {
          id: crypto.randomUUID(),
          email: payload.email,
          displayName: payload.email.split("@")[0] || "Price watcher",
          role: payload.email.toLowerCase().includes("admin") ? "ADMIN" : "USER",
        };
        persistUser(nextUser);
        setUser(nextUser);
      },
      async register(payload) {
        const nextUser: AuthUser = {
          id: crypto.randomUUID(),
          email: payload.email,
          displayName: payload.displayName,
          role: "USER",
        };
        persistUser(nextUser);
        setUser(nextUser);
      },
      logout() {
        localStorage.removeItem(STORAGE_KEY);
        setUser(null);
      },
    };
  }, [user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }

  return context;
}
