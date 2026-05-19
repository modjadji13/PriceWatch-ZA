import {
  Award,
  BarChart2,
  Bell,
  ChartNoAxesColumnIncreasing,
  LogOut,
  Shield,
  TrendingDown,
  UserRound,
  X,
  Zap,
} from "lucide-react";
import type { ReactNode } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";

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
        <aside className="filter-sidebar" aria-label="Product filters">
          <div className="filter-panel">
            <FilterGroup title="Active Filters">
              <div className="filter-tags">
                <span>
                  Coffee
                  <X size={12} />
                </span>
                <span>
                  In Stock
                  <X size={12} />
                </span>
              </div>
            </FilterGroup>

            <FilterGroup title="Categories">
              <CheckRow label="Beverages" count="128" checked />
              <CheckRow label="Pantry" count="45" />
              <CheckRow label="Dairy" count="32" />
              <CheckRow label="Snacks" count="89" />
            </FilterGroup>

            <FilterGroup title="Stores">
              <CheckRow label="Checkers" checked />
              <CheckRow label="Pick n Pay" checked />
              <CheckRow label="Takealot" checked />
              <CheckRow label="Makro" />
              <CheckRow label="Woolworths" />
            </FilterGroup>

            <FilterGroup title="Price Range" meta="R0 - R500">
              <input className="range-input" type="range" min="0" max="1000" defaultValue="500" />
              <div className="price-inputs">
                <label>
                  <span>R</span>
                  <input type="text" placeholder="Min" />
                </label>
                <label>
                  <span>R</span>
                  <input type="text" placeholder="Max" />
                </label>
              </div>
            </FilterGroup>

            <FilterGroup title="Price Status">
              <CheckRow label="Live price" marker="green" checked />
              <CheckRow label="Estimated price" marker="yellow" />
              <CheckRow label="Price dropped" icon={<TrendingDown size={13} />} />
              <CheckRow label="Lowest in 30 days" icon={<Award size={13} />} />
            </FilterGroup>

            <FilterGroup title="Account">
              <Link to="/app/watchlist" className="sidebar-link">
                <ChartNoAxesColumnIncreasing size={14} />
                Watchlist
              </Link>
              <Link to="/app/alerts" className="sidebar-link">
                <Bell size={14} />
                Alerts
              </Link>
              <Link to="/admin" className="sidebar-link">
                <Shield size={14} />
                Admin
              </Link>
            </FilterGroup>
          </div>
        </aside>

        <main className="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function FilterGroup({
  title,
  meta,
  children,
}: {
  title: string;
  meta?: string;
  children: ReactNode;
}) {
  return (
    <section className="filter-group">
      <div className="filter-title-row">
        <h3>{title}</h3>
        {meta && <span>{meta}</span>}
      </div>
      <div className="filter-content">{children}</div>
    </section>
  );
}

function CheckRow({
  label,
  count,
  checked,
  marker,
  icon,
}: {
  label: string;
  count?: string;
  checked?: boolean;
  marker?: "green" | "yellow";
  icon?: ReactNode;
}) {
  return (
    <label className="check-row">
      <input type="checkbox" defaultChecked={checked} />
      {marker && <span className={`status-dot ${marker}`} />}
      {icon && <span className="filter-icon">{icon}</span>}
      <span>{label}</span>
      {count && <small>{count}</small>}
    </label>
  );
}
