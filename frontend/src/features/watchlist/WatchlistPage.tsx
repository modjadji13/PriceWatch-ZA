import { Search } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/ui/EmptyState";

const sampleItems = [
  { id: 1, name: "Milk 2L", category: "GROCERY", target: "Track weekly" },
  { id: 2, name: "Rice 10kg", category: "GROCERY", target: "Buy below R180" },
];

export function WatchlistPage() {
  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Private</p>
          <h1>Watchlist</h1>
          <p>Saved products will live here once the backend watchlist endpoints are added.</p>
        </div>
        <Link to="/" className="primary-button inline-button">
          <Search size={17} />
          Search product
        </Link>
      </div>

      <div className="card-grid">
        {sampleItems.map((item) => (
          <article className="data-card" key={item.id}>
            <span>{item.category.toLowerCase()}</span>
            <h2>{item.name}</h2>
            <p>{item.target}</p>
          </article>
        ))}
      </div>

      <EmptyState
        title="Backend connection still planned"
        description="The route guard and page are ready. Persisting user watchlist items needs /api/watchlist."
      />
    </section>
  );
}
