import { useEffect } from "react";
import { Navigate } from "react-router-dom";
import { useAuthModal } from "./AuthModalProvider";
import type { AuthModalMode } from "./authTypes";

// /login and /register are no longer pages, but old links and bookmarks still
// point at them, so they land on the home page with the right modal open.
export function AuthRedirect({ mode }: { mode: AuthModalMode }) {
  const { openAuthModal } = useAuthModal();

  useEffect(() => {
    openAuthModal(mode);
  }, [mode, openAuthModal]);

  return <Navigate to="/" replace />;
}
