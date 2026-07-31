export default function EvidenceLink({ evidence, onNavigate }) {
  return (
    <button
      type="button"
      onClick={() => onNavigate?.(evidence)}
      className="rounded-full border border-brand-200 bg-brand-50 px-2.5 py-1 text-xs text-brand-700 hover:bg-brand-100"
      aria-label={`查看证据：${evidence.label}`}
    >
      {evidence.label}
    </button>
  )
}
