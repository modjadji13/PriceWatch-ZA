import { FormEvent, useState } from "react";
import { LogIn } from "lucide-react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "./AuthProvider";

type LocationState = {
  from?: string | { pathname?: string; search?: string };
};

function resolveRedirect(state: LocationState | null) {
  const from = state?.from;
  if (typeof from === "string") {
    return from;
  }

  if (from?.pathname) {
    return `${from.pathname}${from.search ?? ""}`;
  }

  return "/app/watchlist";
}

export function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    await login({ email, password });
    navigate(resolveRedirect(location.state as LocationState | null), { replace: true });
  }

  return (
    <>
      <p className="eyebrow">Account</p>
      <h1>Log in</h1>
      <p>Search stays public. Log in when you want watchlists, alerts, profile, or admin access.</p>

      <form className="stacked-form" onSubmit={handleSubmit}>
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="text"
          inputMode="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />

        <button type="submit" className="primary-button">
          <LogIn size={17} />
          Log in
        </button>
      </form>

      <p className="auth-note">
        No account yet? <Link to="/register">Create one</Link>
      </p>
    </>
  );
}
