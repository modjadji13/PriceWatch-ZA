import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Apple, AtSign, LogIn, UserPlus, X } from "lucide-react";
import { useAuth } from "./AuthProvider";
import { useAuthModal } from "./AuthModalProvider";
import type { AuthModalMode } from "./authTypes";

const COPY: Record<AuthModalMode, { title: string; blurb: string }> = {
  login: {
    title: "Log in",
    blurb: "Search stays public. Log in for watchlists, alerts and your profile.",
  },
  register: {
    title: "Sign up",
    blurb: "Save products and set your own alert thresholds.",
  },
};

export function AuthPopover({ mode }: { mode: AuthModalMode }) {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showEmailForm, setShowEmailForm] = useState(false);
  const { login, register } = useAuth();
  const { openAuthModal, closeAuthModal } = useAuthModal();
  const panelRef = useRef<HTMLDivElement>(null);
  const firstFieldRef = useRef<HTMLInputElement>(null);

  // The dialog closes on Escape or an outside click. Header auth triggers live
  // outside the panel, so the account-actions guard lets them switch modes.
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
  }, [mode, showEmailForm]);

  function switchMode(next: AuthModalMode) {
    setDisplayName("");
    setEmail("");
    setPassword("");
    setShowEmailForm(false);
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
  const showAuthChoices = !showEmailForm;

  const panel = (
    <div
      className="auth-popover auth-dialog"
      ref={panelRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby="auth-popover-title"
    >
      <button
        type="button"
        className="auth-dialog-close"
        onClick={closeAuthModal}
        aria-label={mode === "register" ? "Close sign up" : "Close sign in"}
      >
        <X size={22} />
      </button>

      <h2 id="auth-popover-title">{copy.title}</h2>

      {showAuthChoices ? (
        <>
          <div className="signup-options">
            <button
              type="button"
              className="signup-provider dark"
              disabled
              title={`Google ${mode === "register" ? "sign-up" : "login"} coming soon`}
            >
              <GoogleMark />
              <span>Continue with Google</span>
            </button>
            <button
              type="button"
              className="signup-provider dark"
              disabled
              title={`Apple ${mode === "register" ? "sign-up" : "login"} coming soon`}
            >
              <Apple size={20} fill="currentColor" />
              <span>Continue with Apple</span>
            </button>

            {mode === "login" ? (
              <>
                <input
                  className="signup-email-input"
                  ref={firstFieldRef}
                  type="email"
                  inputMode="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="Email"
                  aria-label="Email"
                />

                <div className="signup-divider"><span>or</span></div>
              </>
            ) : null}

            <button type="button" className="signup-provider email" onClick={() => setShowEmailForm(true)}>
              <AtSign size={20} />
              <span>{mode === "register" ? "Continue with Email" : "Log in with Email"}</span>
            </button>
          </div>

          {mode === "login" ? (
            <button
              type="button"
              className="forgot-password-button"
              disabled
              title="Password recovery coming soon"
            >
              Forgot password?
            </button>
          ) : null}

          <p className="signup-legal">
            By continuing, you acknowledge and agree to PriceWatchZA&apos;s legal terms, which we recommend reviewing.
          </p>
        </>
      ) : (
        <>
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
        </>
      )}
    </div>
  );

  return <div className="auth-modal-backdrop">{panel}</div>;
}

function GoogleMark() {
  return (
    <svg className="google-mark" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.19-2.07H12v3.92h5.38a4.6 4.6 0 0 1-2 3.02v2.54h3.24c1.9-1.75 2.98-4.33 2.98-7.41Z" />
      <path fill="#34A853" d="M12 22c2.7 0 4.98-.9 6.63-2.36l-3.24-2.54c-.9.6-2.05.96-3.39.96-2.61 0-4.82-1.76-5.61-4.13H3.04v2.62A10 10 0 0 0 12 22Z" />
      <path fill="#FBBC05" d="M6.39 13.93A6 6 0 0 1 6.08 12c0-.67.11-1.32.31-1.93V7.45H3.04A10 10 0 0 0 2 12c0 1.61.38 3.14 1.04 4.55l3.35-2.62Z" />
      <path fill="#EA4335" d="M12 5.94c1.47 0 2.79.51 3.83 1.5l2.87-2.88A9.63 9.63 0 0 0 12 2a10 10 0 0 0-8.96 5.45l3.35 2.62C7.18 7.7 9.39 5.94 12 5.94Z" />
    </svg>
  );
}
