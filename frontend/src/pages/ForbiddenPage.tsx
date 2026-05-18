import { Link } from "react-router-dom";

export function ForbiddenPage() {
  return (
    <section className="content-section narrow">
      <p className="eyebrow">403</p>
      <h1>Access restricted</h1>
      <p>This route is only available to users with the right role.</p>
      <Link to="/" className="primary-button inline-button">
        Back to search
      </Link>
    </section>
  );
}
