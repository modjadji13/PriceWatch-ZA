import { BarChart2, LogOut, UserRound } from "lucide-react";
import { Link, NavLink, Outlet, useSearchParams } from "react-router-dom";
import { useAuthModal } from "../../features/auth/AuthModalProvider";
import { AuthPopover } from "../../features/auth/AuthPopover";
import { useAuth } from "../../features/auth/AuthProvider";
import { FilterSidebar } from "./FilterSidebar";

export function AppLayout() {
  const { user, logout } = useAuth();
  const { mode, openAuthModal } = useAuthModal();
  const [searchParams] = useSearchParams();
  const selectedCategory = searchParams.get("category")?.toUpperCase();

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink to="/" className="brand" aria-label="PriceWatchZA home">
          <BarChart2 size={20} />
          <strong>PriceWatchZA</strong>
        </NavLink>

        <nav className="nav-links" aria-label="Primary navigation">
          <CategoryLink category="GROCERY" label="Groceries" selectedCategory={selectedCategory} />
          <CategoryLink category="ELECTRONICS" label="Electronics" selectedCategory={selectedCategory} />
          <CategoryLink category="HOUSEHOLD" label="Household" selectedCategory={selectedCategory} />
          <CategoryLink category="HEALTH" label="Health" selectedCategory={selectedCategory} />
          <CategoryLink category="BEAUTY" label="Beauty" selectedCategory={selectedCategory} />
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
              <div className="auth-anchor">
                <button
                  type="button"
                  className="plain-auth-button"
                  aria-expanded={mode === "login"}
                  onClick={() => openAuthModal("login")}
                >
                  Sign in
                </button>
                {mode === "login" ? <AuthPopover mode="login" /> : null}
              </div>

              <div className="auth-anchor">
                <button
                  type="button"
                  className="primary-button compact"
                  aria-expanded={mode === "register"}
                  onClick={() => openAuthModal("register")}
                >
                  Register
                </button>
                {mode === "register" ? <AuthPopover mode="register" /> : null}
              </div>
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

function CategoryLink({
  category,
  label,
  selectedCategory,
}: {
  category: string;
  label: string;
  selectedCategory?: string;
}) {
  return (
    <Link to={`/?category=${category}`} className={selectedCategory === category ? "active" : undefined}>
      {label}
    </Link>
  );
}
