import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { AuthModalMode } from "./authTypes";

type AuthModalContextValue = {
  mode: AuthModalMode | null;
  openAuthModal: (mode?: AuthModalMode) => void;
  closeAuthModal: () => void;
};

const AuthModalContext = createContext<AuthModalContextValue | null>(null);

// State only. The panel itself renders inside the header so it can sit directly
// under whichever account button opened it.
export function AuthModalProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<AuthModalMode | null>(null);

  const openAuthModal = useCallback((next: AuthModalMode = "login") => setMode(next), []);
  const closeAuthModal = useCallback(() => setMode(null), []);

  const value = useMemo<AuthModalContextValue>(
    () => ({ mode, openAuthModal, closeAuthModal }),
    [mode, openAuthModal, closeAuthModal],
  );

  return <AuthModalContext.Provider value={value}>{children}</AuthModalContext.Provider>;
}

export function useAuthModal() {
  const context = useContext(AuthModalContext);
  if (!context) {
    throw new Error("useAuthModal must be used inside AuthModalProvider");
  }

  return context;
}
