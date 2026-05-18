import { FormEvent, useState } from "react";
import { Search, ShieldCheck, TrendingDown, WalletCards } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { categories } from "./priceTypes";

export function SearchPage() {
  const [product, setProduct] = useState("");
  const [category, setCategory] = useState("GROCERY");
  const navigate = useNavigate();

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = product.trim();
    if (!trimmed) {
      return;
    }

    navigate(`/results?product=${encodeURIComponent(trimmed)}&category=${encodeURIComponent(category)}`);
  }

  return (
    <section className="search-page">
      <div className="search-hero">
        <div className="hero-copy">
          <h1>Compare South African prices before you buy.</h1>
          <p>Search publicly, compare stores immediately, and only log in when you want to save watches or alerts.</p>
        </div>

        <form className="search-form" onSubmit={handleSubmit}>
          <label htmlFor="product">Product</label>
          <div className="search-row">
            <input
              id="product"
              value={product}
              onChange={(event) => setProduct(event.target.value)}
              placeholder="Milk, rice, headphones..."
            />
            <select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Category">
              {categories.map((item) => (
                <option key={item} value={item}>
                  {item.toLowerCase()}
                </option>
              ))}
            </select>
            <button type="submit" className="primary-button">
              <Search size={18} />
              Compare
            </button>
          </div>
        </form>
      </div>

      <div className="metrics-grid">
        <article>
          <TrendingDown size={22} />
          <h2>Lowest visible price</h2>
          <p>Quickly spot which store has the better current offer.</p>
        </article>
        <article>
          <WalletCards size={22} />
          <h2>No account needed</h2>
          <p>Search and compare without signup friction.</p>
        </article>
        <article>
          <ShieldCheck size={22} />
          <h2>Private when personal</h2>
          <p>Login is reserved for watchlists, alerts, profile, and admin workflows.</p>
        </article>
      </div>
    </section>
  );
}
