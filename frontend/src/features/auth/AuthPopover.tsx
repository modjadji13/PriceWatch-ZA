import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { LogIn, UserPlus } from "lucide-react";
import { useAuth } from "./AuthProvider";
import { useAuthModal } from "./AuthModalProvider";
import type { AuthModalMode } from "./authTypes";

const COPY: Record<AuthModalMode, { title: string; blurb: string }> = {
  login: {
    title: "Log in",
    blurb: "Search stays public. Log in for watchlists, alerts and your profile.",
  },
  register: {
    title: "Create account",
    blurb: "Save products and set your own alert thresholds.",
  },
};

export function AuthPopover({ mode }: { mode: AuthModalMode }) {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const { login, register } = useAuth();
  const { openAuthModal, closeAuthModal } = useAuthModal();
  const panelRef = useRef<HTMLDivElement>(null);
  const firstFieldRef = useRef<HTMLInputElement>(null);

  // A panel anchored to the header closes the way menus do: Escape, or a click
  // anywhere outside it. The trigger buttons live outside the panel, so they
  // re-open on the same click that closes it — hence the .account-actions guard.
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        closeAuthModal();
      }
    }

    function handlePointerDown(event: MouseEvent) {
      const target = event.target as HTMLElement;
      if (!panelRef.current?.contains(target) && !target.closest(".account-actions")) {
        closeAuthModal();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("mousedown", handlePointerDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("mousedown", handlePointerDown);
    };
  }, [closeAuthModal]);

  useEffect(() => {
    firstFieldRef.current?.focus();
  }, [mode]);

  function switchMode(next: AuthModalMode) {
    setDisplayName("");
    setEmail("");
    setPassword("");
    openAuthModal(next);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (mode === "login") {
      await login({ email, password });
    } else {
      await register({ displayName, email, password });
    }

    closeAuthModal();
  }

  const copy = COPY[mode];

  return (
    <div
      className="auth-popover"
      ref={panelRef}
      role="dialog"
      aria-modal="false"
      aria-labelledby="auth-popover-title"
    >
      <h2 id="auth-popover-title">{copy.title}</h2>
      <p className="auth-popover-blurb">{copy.blurb}</p>

      <form className="stacked-form compact-form" onSubmit={handleSubmit}>
        {mode === "register" ? (
          <>
            <label htmlFor="auth-display-name">Display name</label>
            <input
              id="auth-display-name"
              ref={firstFieldRef}
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              required
            />
          </>
        ) : null}

        <label htmlFor="auth-email">Email</label>
        <input
          id="auth-email"
          ref={mode === "login" ? firstFieldRef : undefined}
          type="text"
          inputMode="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />

        <label htmlFor="auth-password">Password</label>
        <input
          id="auth-password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
          minLength={mode === "register" ? 8 : undefined}
        />

        <button type="submit" className="primary-button">
          {mode === "login" ? <LogIn size={15} /> : <UserPlus size={15} />}
          {mode === "login" ? "Log in" : "Register"}
        </button>
      </form>

      <p className="auth-note">
        {mode === "login" ? (
          <>
            No account yet?{" "}
            <button type="button" className="link-button" onClick={() => switchMode("register")}>
              Create one
            </button>
          </>
        ) : (
          <>
            Already have an account?{" "}
            <button type="button" className="link-button" onClick={() => switchMode("login")}>
              Log in
            </button>
          </>
        )}
      </p>
    </div>
  );
}
