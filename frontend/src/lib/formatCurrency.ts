const zarFormatter = new Intl.NumberFormat("en-ZA", {
  style: "currency",
  currency: "ZAR",
});

export function formatCurrency(amount: number) {
  return zarFormatter.format(amount);
}
