import { Command, Search, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

const commands = [
  ["Dashboard", "/app/dashboard"],
  ["Inventory", "/app/inventory"],
  ["Inbound receiving", "/app/inventory/inbound"],
  ["Medicines", "/app/medicines"],
  ["Batches", "/app/batches"],
  ["Suppliers", "/app/suppliers"],
  ["Warehouses", "/app/warehouses"],
  ["Transfers", "/app/transfers"],
  ["Shipments", "/app/shipments"],
  ["Tracking", "/app/tracking"],
  ["Batch labels", "/app/labels"],
  ["Scanner", "/app/scanner"],
  ["Reports", "/app/reports"],
  ["Audit logs", "/app/audit"],
  ["Notifications", "/app/notifications"]
] as const;

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const navigate = useNavigate();

  const results = useMemo(
    () => commands.filter(([name]) => name.toLowerCase().includes(query.toLowerCase())),
    [query]
  );

  useEffect(() => {
    setSelectedIndex(0);
  }, [results]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen(v => !v);
      }
      if (e.key === "Escape") {
        setOpen(false);
      }
      if (open) {
        if (e.key === "ArrowDown") {
          e.preventDefault();
          setSelectedIndex(i => (i + 1) % (results.length || 1));
        } else if (e.key === "ArrowUp") {
          e.preventDefault();
          setSelectedIndex(i => (i - 1 + results.length) % (results.length || 1));
        } else if (e.key === "Enter" && results[selectedIndex]) {
          e.preventDefault();
          navigate(results[selectedIndex][1]);
          setOpen(false);
          setQuery("");
        }
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, results, selectedIndex, navigate]);

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="hidden items-center gap-1 rounded border border-border px-2 py-1 text-xs text-muted md:inline-flex"
        aria-label="Open command palette"
      >
        <Command size={14} />
        <span>⌘K</span>
      </button>
    );
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Command palette"
      className="fixed inset-0 z-50 grid place-items-start bg-black/40 p-4 pt-[15vh]"
      onMouseDown={() => setOpen(false)}
    >
      <div
        className="w-full max-w-xl rounded-lg border border-border bg-canvas shadow-2xl"
        onMouseDown={e => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 border-b border-border p-3">
          <Search size={18} className="text-muted" />
          <input
            autoFocus
            className="w-full bg-transparent text-ink outline-none"
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Type a command or jump to page…"
            aria-label="Search commands"
          />
          <button aria-label="Close command palette" onClick={() => setOpen(false)}>
            <X size={18} className="text-muted hover:text-ink" />
          </button>
        </div>
        <ul className="max-h-80 overflow-y-auto p-2" role="listbox">
          {results.map(([name, path], idx) => (
            <li key={path} role="option" aria-selected={idx === selectedIndex}>
              <button
                className={`w-full rounded px-3 py-2 text-left text-sm ${
                  idx === selectedIndex ? "bg-surface text-ink font-medium" : "text-muted hover:bg-surface-soft"
                }`}
                onClick={() => {
                  navigate(path);
                  setOpen(false);
                  setQuery("");
                }}
              >
                {name}
              </button>
            </li>
          ))}
          {!results.length && (
            <li className="p-3 text-center text-sm text-muted">No matching commands.</li>
          )}
        </ul>
      </div>
    </div>
  );
}