import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <section className="content-section narrow">
      <p className="eyebrow">404</p>
      <h1>Page not found</h1>
      <p>The page you opened does not exist.</p>
      <Link to="/" className="primary-button inline-button">
        Back to search
      </Link>
    </section>
  );
}
