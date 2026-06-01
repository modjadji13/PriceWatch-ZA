import { FormEvent, useState } from "react";
import { Heart, LayoutGrid, List, Search } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { categories } from "./priceTypes";

type SaleProduct = {
  id: number;
  category: string;
  title: string;
  price: string;
  oldPrice: string;
  store: string;
  stores: number;
  range: string;
  badge: string;
  badgeTone: string;
  storesCompared: string[];
  estimated: boolean;
  imageShape: string;
  imageUrl: string;
  saved?: boolean;
};

const FEATURED_PRODUCTS: SaleProduct[] = [
  {
    id: 1,
    category: "Pantry",
    title: "Selati White Sugar 500g",
    price: "R14.99",
    oldPrice: "R18.99",
    store: "Checkers",
    stores: 4,
    range: "R14 - R22",
    badge: "-21%",
    badgeTone: "red",
    storesCompared: ["Checkers", "Pick n Pay", "Makro"],
    estimated: false,
    imageShape: "square",
    imageUrl: "https://img.mrdfood.com/fit-in/filters:format(jpeg):fill(white):background_color(ffffff)/480x480/groceries/product/6b987fe1-d2b0-402b-aa14-f59e4571fe3b.png",
  },
  {
    id: 2,
    category: "Dairy",
    title: "Clover Fresh Full Cream Milk 2L",
    price: "R31.99",
    oldPrice: "R39.99",
    store: "Pick n Pay",
    stores: 5,
    range: "R31 - R45",
    badge: "-20%",
    badgeTone: "red",
    storesCompared: ["Pick n Pay", "Checkers", "Makro"],
    estimated: false,
    imageShape: "tall",
    imageUrl: "https://www.clover.co.za/wp-content/uploads/2018/05/Fresh-fullcream-2l-2024_featured.png",
  },
  {
    id: 3,
    category: "Household",
    title: "Sunlight Dishwashing Liquid Lemon 750ml",
    price: "R24.99",
    oldPrice: "R34.99",
    store: "Takealot",
    stores: 3,
    range: "R24 - R38",
    badge: "-29%",
    badgeTone: "red",
    storesCompared: ["Takealot", "Makro", "Checkers"],
    estimated: false,
    saved: true,
    imageShape: "tall",
    imageUrl: "https://originsworldfoods.com/cdn/shop/products/112762_1200x1200.jpg?v=1636964920",
  },
  {
    id: 4,
    category: "Groceries",
    title: "Tastic Parboiled Rice 2kg",
    price: "R34.99",
    oldPrice: "R42.99",
    store: "Makro",
    stores: 5,
    range: "R34 - R49",
    badge: "-19%",
    badgeTone: "red",
    storesCompared: ["Makro", "Checkers", "Pick n Pay"],
    estimated: false,
    imageShape: "tall",
    imageUrl: "https://welkomusa.com/cdn/shop/files/tastic_2kg_1200x1504.png?v=1771870864",
  },
];

const STORE_LOGOS: Record<string, string> = {
  Checkers: "https://www.google.com/s2/favicons?domain=checkers.co.za&sz=64",
  "Pick n Pay": "https://www.google.com/s2/favicons?domain=pnp.co.za&sz=64",
  Takealot: "https://www.google.com/s2/favicons?domain=takealot.com&sz=64",
  Makro: "https://www.google.com/s2/favicons?domain=makro.co.za&sz=64",
  Woolworths: "https://www.google.com/s2/favicons?domain=woolworths.co.za&sz=64",
};

export function SearchPage() {
  const [product, setProduct] = useState("Items on sale");
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
    <section className="dashboard-page">
      <div className="search-toolbar">
        <div className="toolbar-row">
          <form className="toolbar-search" onSubmit={handleSubmit}>
            <Search size={16} />
            <input
              id="product"
              value={product}
              onChange={(event) => setProduct(event.target.value)}
              placeholder="Search products across all stores..."
            />
            <kbd>Ctrl K</kbd>
          </form>

          <div className="category-select">
            <select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Category">
              {categories.map((item) => (
                <option key={item} value={item}>
                  {item.toLowerCase()}
                </option>
              ))}
            </select>
          </div>

          <div className="sort-pills">
            <span>Sort by:</span>
            <button type="button" className="active">Relevance</button>
            <button type="button">Lowest Price</button>
            <button type="button">Biggest Drop</button>
            <button type="button">Rating</button>
          </div>
        </div>

        <div className="result-count-row">
          <span>
            Showing <strong>1-12</strong> of <strong>64</strong> current sale items
          </span>
          <div>
            <button type="button" aria-label="Grid view">
              <LayoutGrid size={16} />
            </button>
            <button type="button" aria-label="List view">
              <List size={16} />
            </button>
          </div>
        </div>
      </div>

      <div className="product-grid-shell">
        <div className="product-grid">
          {FEATURED_PRODUCTS.map((item) => (
            <ProductCard key={item.id} product={item} />
          ))}
        </div>

        <DashboardFooter />
      </div>
    </section>
  );
}

function ProductCard({ product }: { product: SaleProduct }) {
  return (
    <article className="product-card">
      <div className="product-art">
        {product.imageUrl ? (
          <img className="sale-product-image" src={product.imageUrl} alt={product.title} />
        ) : (
          <div className={`image-placeholder ${product.imageShape}`}>
            <span>Image</span>
          </div>
        )}
        {product.badge && <small className={`deal-badge ${product.badgeTone}`}>{product.badge}</small>}
        <button className={product.saved ? "heart-button saved" : "heart-button"} type="button" aria-label="Save product">
          <Heart size={16} fill={product.saved ? "currentColor" : "none"} />
        </button>
      </div>

      <div className="product-card-body">
        <div className="product-category">{product.category}</div>
        <h2>{product.title}</h2>

        <div className="price-block">
          <div className="price-line">
            <strong>{product.price}</strong>
            {product.oldPrice && <span>{product.oldPrice}</span>}
          </div>
          <p>
            <i className={product.estimated ? "status-dot yellow" : "status-dot green"} />
            {product.estimated ? "Est. price at " : "Live price at "}
            <b>{product.store}</b>
          </p>

          <div className="store-strip">
            <div className="store-icons">
              {product.storesCompared.map((store) => (
                <span key={store} className="store-avatar" title={store}>
                  <img src={STORE_LOGOS[store]} alt="" />
                </span>
              ))}
            </div>
            <span>{product.stores} stores</span>
            <strong>{product.range}</strong>
          </div>
        </div>
      </div>

    </article>
  );
}

function DashboardFooter() {
  return (
    <footer className="dashboard-footer">
      <div>
        <h4>PriceWatchZA</h4>
        <p>Transparent price tracking and comparison for South African consumers.</p>
      </div>
      <div>
        <h4>Categories</h4>
        <a>Groceries</a>
        <a>Electronics</a>
        <a>Household</a>
      </div>
      <div>
        <h4>Monitored Stores</h4>
        <a>Checkers</a>
        <a>Pick n Pay</a>
        <a>Takealot</a>
      </div>
      <div>
        <h4>Platform</h4>
        <a>API Access</a>
        <a>Privacy Policy</a>
        <a>Terms of Service</a>
      </div>
    </footer>
  );
}
