import { Outlet } from "react-router-dom";

export function AuthLayout() {
  return (
    <div className="auth-layout">
      <div className="auth-panel">
        <Outlet />
      </div>
    </div>
  );
}
