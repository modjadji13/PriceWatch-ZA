export type ProductFallback = {
  category: string;
  name: string;
  imageUrl: string;
};

type ProductFallbackSource = Omit<ProductFallback, "imageUrl"> & {
  keywords: string[];
  imageUrl?: string;
  fallbackImage: {
    label: string;
    tone: string;
    shape: "bag" | "bottle" | "box";
  };
};

const PRODUCT_FALLBACKS: ProductFallbackSource[] = [
  {
    keywords: ["sugar", "selati"],
    category: "Pantry",
    name: "Selati White Sugar 500g",
    imageUrl: "https://img.mrdfood.com/fit-in/filters:format(jpeg):fill(white):background_color(ffffff)/480x480/groceries/product/6b987fe1-d2b0-402b-aa14-f59e4571fe3b.png",
    fallbackImage: { label: "Selati Sugar 500g", tone: "#f59e0b", shape: "bag" },
  },
  {
    keywords: ["salt", "cerebos"],
    category: "Pantry",
    name: "Cerebos Iodated Table Salt 500g",
    imageUrl: "https://assets.woolworthsstatic.co.za/Cerebos-Iodated-Table-Salt-500-g-6001021021023.jpg",
    fallbackImage: { label: "Cerebos Salt 500g", tone: "#2563eb", shape: "box" },
  },
  {
    keywords: ["water", "aquelle", "bonaqua"],
    category: "Beverages",
    name: "aQuelle Still Natural Spring Water 500ml",
    imageUrl: "https://i0.wp.com/aquelle.co.za/wp-content/uploads/2025/06/aQuelle-Still-Natural-Spring-Water-500ml.png?ssl=1&w=1290",
    fallbackImage: { label: "aQuelle Water 500ml", tone: "#0ea5e9", shape: "bottle" },
  },
  {
    keywords: ["milk", "clover"],
    category: "Dairy",
    name: "Clover Fresh Full Cream Milk 2L",
    imageUrl: "https://www.clover.co.za/wp-content/uploads/2018/05/Fresh-fullcream-2l-2024_featured.png",
    fallbackImage: { label: "Clover Milk 2L", tone: "#2563eb", shape: "bottle" },
  },
  {
    keywords: ["sunlight", "dishwashing", "liquid"],
    category: "Household",
    name: "Sunlight Dishwashing Liquid Lemon 750ml",
    imageUrl: "https://originsworldfoods.com/cdn/shop/products/112762_1200x1200.jpg?v=1636964920",
    fallbackImage: { label: "Sunlight 750ml", tone: "#16a34a", shape: "bottle" },
  },
  {
    keywords: ["rice", "tastic"],
    category: "Groceries",
    name: "Tastic Parboiled Rice 2kg",
    imageUrl: "https://welkomusa.com/cdn/shop/files/tastic_2kg_1200x1504.png?v=1771870864",
    fallbackImage: { label: "Tastic Rice 2kg", tone: "#eab308", shape: "bag" },
  },
  {
    keywords: ["bread", "albany", "sasko"],
    category: "Bakery",
    name: "Albany Superior White Bread 700g",
    fallbackImage: { label: "Albany Bread 700g", tone: "#ef4444", shape: "bag" },
  },
  {
    keywords: ["coffee", "ricoffy", "nescafe"],
    category: "Pantry",
    name: "Nescafe Ricoffy Coffee 750g",
    fallbackImage: { label: "Ricoffy 750g", tone: "#92400e", shape: "box" },
  },
];

export function productFallbackFor(productName: string) {
  const normalizedProduct = productName.toLowerCase();

  const fallback = PRODUCT_FALLBACKS.find((item) =>
    item.keywords.some((keyword) => normalizedProduct.includes(keyword))
  );

  if (!fallback) {
    return undefined;
  }

  return {
    category: fallback.category,
    name: fallback.name,
    imageUrl: fallback.imageUrl ?? productImageDataUrl(fallback.fallbackImage),
  };
}

function productImageDataUrl(config: ProductFallbackSource["fallbackImage"]) {
  const safeLabel = escapeSvgText(config.label);
  const body =
    config.shape === "bottle"
      ? `<path d="M110 42h30v24l12 18v94c0 18-12 30-30 30s-30-12-30-30V84l12-18V42z" fill="#ffffff" stroke="#cbd5e1" stroke-width="5"/><rect x="96" y="108" width="48" height="58" rx="8" fill="${config.tone}"/>`
      : config.shape === "bag"
        ? `<path d="M72 56c20-12 76-12 96 0l12 142c-28 14-96 14-124 0L72 56z" fill="#ffffff" stroke="#cbd5e1" stroke-width="5"/><rect x="78" y="102" width="84" height="54" rx="8" fill="${config.tone}"/>`
        : `<rect x="72" y="54" width="96" height="144" rx="12" fill="#ffffff" stroke="#cbd5e1" stroke-width="5"/><rect x="84" y="96" width="72" height="58" rx="8" fill="${config.tone}"/>`;
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" role="img" aria-label="${safeLabel}">
      <rect width="240" height="240" fill="#f8fafc"/>
      ${body}
      <text x="120" y="135" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" font-weight="800" fill="#ffffff">${safeLabel}</text>
    </svg>
  `;

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function escapeSvgText(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
