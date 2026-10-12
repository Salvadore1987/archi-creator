import { useToast } from './toast';

export function Toaster() {
  const toast = useToast((s) => s.toast);
  const dismiss = useToast((s) => s.dismiss);
  if (!toast) {
    return null;
  }
  return (
    <div className={`toast${toast.tone === 'error' ? ' toast--error' : ''}`} role="status">
      <span>{toast.text}</span>
      {toast.action && (
        <button
          type="button"
          className="btn btn--ghost"
          onClick={() => {
            toast.action!.run();
            dismiss(toast.id);
          }}
        >
          {toast.action.label}
        </button>
      )}
    </div>
  );
}
