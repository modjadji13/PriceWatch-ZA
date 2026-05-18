export function StatusBadge({ tone, children }: { tone: "green" | "amber" | "red" | "blue"; children: string }) {
  return <span className={`status-badge ${tone}`}>{children}</span>;
}
