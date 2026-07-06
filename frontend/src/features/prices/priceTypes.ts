export type PriceOffer = {
  store: string;
  amount: number;
  estimated: boolean;
  logoUrl: string;
  productName?: string;
  productImageUrl?: string;
  productCategory?: string;
  productDescription?: string;
  storeOffers?: StoreOffer[];
};

export type StoreOffer = {
  store: string;
  amount: number;
  logoUrl: string;
};

export type ProductDetails = {
  name: string;
  category: string;
  imageUrl: string;
  description: string;
  sourceStore: string;
};

export type PriceComparison = {
  product: string;
  category: string;
  details: ProductDetails;
  prices: PriceOffer[];
};

export type PriceRecord = {
  id: number;
  store: string;
  amount: number;
  recordedAt: string;
};

export type Category = "GROCERY" | "ELECTRONICS" | "HOUSEHOLD" | "HEALTH" | "BEAUTY";

export const categories: Category[] = ["GROCERY", "ELECTRONICS", "HOUSEHOLD", "HEALTH", "BEAUTY"];
