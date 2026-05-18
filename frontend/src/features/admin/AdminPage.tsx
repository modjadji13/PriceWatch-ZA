import { Database, Play, Store } from "lucide-react";

export function AdminPage() {
  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Admin</p>
          <h1>Operations</h1>
          <p>Protected admin workspace for store config, scrape jobs, and product data checks.</p>
        </div>
      </div>

      <div className="card-grid">
        <article className="data-card">
          <Store size={22} />
          <h2>Stores</h2>
          <p>Manage scraper store config once admin endpoints exist.</p>
        </article>
        <article className="data-card">
          <Play size={22} />
          <h2>Scrape jobs</h2>
          <p>Run or inspect scheduled price update jobs.</p>
        </article>
        <article className="data-card">
          <Database size={22} />
          <h2>Data quality</h2>
          <p>Review products, duplicates, and suspicious price records.</p>
        </article>
      </div>
    </section>
  );
}
