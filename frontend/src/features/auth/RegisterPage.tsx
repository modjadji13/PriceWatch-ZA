import { useState } from "react";
import type { FormEvent } from "react";
import { UserPlus } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "./AuthProvider";

export function RegisterPage() {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const { register } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    await register({ displayName, email, password });
    navigate("/app/watchlist", { replace: true });
  }

  return (
    <>
      <p className="eyebrow">Account</p>
      <h1>Create account</h1>
      <p>Use an account to save products and set personal alert thresholds.</p>

      <form className="stacked-form" onSubmit={handleSubmit}>
        <label htmlFor="displayName">Display name</label>
        <input
          id="displayName"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          required
        />

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
          minLength={8}
        />

        <button type="submit" className="primary-button">
          <UserPlus size={17} />
          Register
        </button>
      </form>

      <p className="auth-note">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </>
  );
}
