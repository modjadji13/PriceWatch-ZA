import { Bell, ChartNoAxesColumnIncreasing, LogIn, LogOut, Search, Shield, UserRound } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink to="/" className="brand" aria-label="PriceWatchZA home">
          <span className="brand-mark">PW</span>
          <span>
            <strong>PriceWatchZA</strong>
            <small>South African price tracking</small>
          </span>
        </NavLink>

        <nav className="nav-links" aria-label="Primary navigation">
          <NavLink to="/">
            <Search size={17} />
            Search
          </NavLink>
          <NavLink to="/app/watchlist">
            <ChartNoAxesColumnIncreasing size={17} />
            Watchlist
          </NavLink>
          <NavLink to="/app/alerts">
            <Bell size={17} />
            Alerts
          </NavLink>
          <NavLink to="/admin">
            <Shield size={17} />
            Admin
          </NavLink>
        </nav>

        <div className="account-actions">
          {user ? (
            <>
              <NavLink to="/app/profile" className="profile-pill">
                <UserRound size={16} />
                {user.displayName}
              </NavLink>
              <button type="button" className="icon-button" onClick={logout} aria-label="Log out">
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <button type="button" className="primary-button compact" onClick={() => navigate("/login")}>
              <LogIn size={17} />
              Log in
            </button>
          )}
        </div>
      </header>

      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
