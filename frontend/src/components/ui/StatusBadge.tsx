import { CircleAlert, CheckCircle2, Clock3 } from "lucide-react";

export function StatusBadge({ status }: { status: string }) {
  const s = status.toUpperCase();
  const danger =
    s.includes("EXPIRED") ||
    s.includes("EXPIRY") ||
    s.includes("CRITICAL") ||
    s.includes("FAILED") ||
    s.includes("OUT_") ||
    s.includes("CANCELLED") ||
    s.includes("QUARANTINED") ||
    s.includes("REJECTED");
  const warning =
    !danger &&
    (s.includes("DELAY") ||
      s.includes("LOW") ||
      s.includes("RESERVED") ||
      s.includes("NEAR") ||
      s.includes("PENDING") ||
      s.includes("REQUESTED") ||
      s.includes("WARNING"));
  const Icon = danger ? CircleAlert : warning ? Clock3 : CheckCircle2;

  const colorClass = danger
    ? "border-danger/30 bg-danger-bg text-danger"
    : warning
    ? "border-warning/30 bg-warning-bg text-warning"
    : "border-success/30 bg-success-bg text-success";

  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-semibold ${colorClass}`}
    >
      <Icon size={12} className="shrink-0" />
      <span>{status.replaceAll("_", " ")}</span>
    </span>
  );
}