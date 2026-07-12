import { BarChart2, LogOut, UserRound, Zap } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { FilterSidebar } from "./FilterSidebar";

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink to="/" className="brand" aria-label="PriceWatchZA home">
          <BarChart2 size={20} />
          <strong>PriceWatchZA</strong>
        </NavLink>

        <nav className="nav-links" aria-label="Primary navigation">
          <NavLink to="/results?product=coffee&category=GROCERY">Groceries</NavLink>
          <NavLink to="/results?product=headphones&category=ELECTRONICS">Electronics</NavLink>
          <NavLink to="/results?product=detergent&category=HOUSEHOLD">Household</NavLink>
          <NavLink to="/results?product=vitamins&category=HEALTH">Health</NavLink>
          <NavLink to="/results?product=cream&category=BEAUTY">Beauty</NavLink>
          <span className="nav-divider" />
          <NavLink to="/results?product=flight&category=ELECTRONICS">Flights</NavLink>
          <NavLink to="/results?product=hotel&category=HOUSEHOLD">Accommodation</NavLink>
          <span className="nav-divider" />
          <NavLink to="/results?product=deals&category=GROCERY" className="deals-link">
            <Zap size={14} />
            Deals
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
            <>
              <button type="button" className="plain-auth-button" onClick={() => navigate("/login")}>
                Sign in
              </button>
              <button type="button" className="primary-button compact" onClick={() => navigate("/register")}>
                Register
              </button>
            </>
          )}
        </div>
      </header>

      <div className="workspace">
        <FilterSidebar />

        <main className="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
