import { useEffect } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { EmptyState } from "../../components/ui/EmptyState";
import { useAuthModal } from "./AuthModalProvider";
import { useAuth } from "./AuthProvider";
import type { UserRole } from "./authTypes";

export function ProtectedRoute({ role }: { role?: UserRole }) {
  const { user } = useAuth();
  const { openAuthModal } = useAuthModal();

  // Staying on the requested URL means a successful log in re-renders straight
  // into the page the user asked for, with no redirect back needed.
  useEffect(() => {
    if (!user) {
      openAuthModal("login");
    }
  }, [user, openAuthModal]);

  if (!user) {
    return (
      <EmptyState
        title="Log in to continue"
        description="This page needs an account. Log in and it will load right here."
        action={
          <button type="button" className="primary-button inline-button" onClick={() => openAuthModal("login")}>
            Log in
          </button>
        }
      />
    );
  }

  if (role && user.role !== role) {
    return <Navigate to="/403" replace />;
  }

  return <Outlet />;
}
