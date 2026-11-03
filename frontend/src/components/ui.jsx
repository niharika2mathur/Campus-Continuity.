import { createContext, useCallback, useContext, useState } from 'react';
import { AlertTriangle, CheckCircle2, Info, Loader2, X, XCircle, Inbox } from 'lucide-react';
import { AVAIL_STYLE } from '../lib/util.js';

export const Card = ({ className = '', children, ...p }) => <section className={`card ${className}`} {...p}>{children}</section>;

export const CardHeader = ({ title, hint, actions, icon: Icon }) => (
  <div className="flex items-start justify-between gap-3 border-b border-ink-700 px-4 py-3">
    <div className="flex min-w-0 items-center gap-2">
      {Icon && <Icon size={16} className="shrink-0 text-brand-400" />}
      <div className="min-w-0">
        <h3 className="truncate text-sm font-semibold text-ink-100">{title}</h3>
        {hint && <p className="mt-0.5 text-xs text-ink-300">{hint}</p>}
      </div>
    </div>
    {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
  </div>
);

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-white">{title}</h1>
        {subtitle && <p className="mt-1 max-w-3xl text-sm text-ink-300">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Stat({ label, value, sub, tone = 'default', icon: Icon, tip }) {
  const tones = { default: 'text-white', good: 'text-teal-300', warn: 'text-amber-300', bad: 'text-red-300', brand: 'text-brand-400' };
  return (
    <Card className="p-4" title={tip}>
      <div className="flex items-center justify-between">
        <span className="text-[11px] font-semibold uppercase tracking-wider text-ink-300">{label}</span>
        {Icon && <Icon size={15} className="text-ink-400" />}
      </div>
      <div className={`mt-2 text-2xl font-semibold tabular-nums ${tones[tone]}`}>{value}</div>
      {sub && <div className="mt-1 text-xs text-ink-400">{sub}</div>}
    </Card>
  );
}

export function Badge({ children, tone = 'neutral', className = '' }) {
  const tones = {
    neutral: 'bg-ink-700/60 text-ink-200 border-ink-600', good: 'bg-teal-500/10 text-teal-300 border-teal-500/30',
    warn: 'bg-amber-500/10 text-amber-300 border-amber-500/30', bad: 'bg-red-500/10 text-red-300 border-red-500/30',
    brand: 'bg-brand-500/10 text-brand-400 border-brand-500/30',
  };
  return <span className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium ${tones[tone]} ${className}`}>{children}</span>;
}

export const StatusBadge = ({ status }) => <span className={`inline-flex rounded-md border px-2 py-0.5 text-[11px] font-medium ${AVAIL_STYLE[status] || AVAIL_STYLE.DEACTIVATED}`}>{status}</span>;

export const Spinner = ({ label = 'Loading…' }) => (
  <div className="flex items-center justify-center gap-2 py-12 text-sm text-ink-300" role="status"><Loader2 size={16} className="animate-spin" />{label}</div>
);

export function Empty({ title, text, action, icon: Icon = Inbox }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <div className="mb-3 rounded-full border border-ink-600 bg-ink-800 p-3"><Icon size={20} className="text-ink-300" /></div>
      <p className="text-sm font-medium text-ink-100">{title}</p>
      {text && <p className="mt-1 max-w-md text-xs text-ink-300">{text}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export const ErrorBox = ({ error, retry }) => (
  <div className="flex items-start gap-3 rounded-xl border border-red-500/30 bg-red-500/5 p-4" role="alert">
    <XCircle size={18} className="mt-0.5 shrink-0 text-red-400" />
    <div className="min-w-0 flex-1"><p className="text-sm font-medium text-red-200">Something went wrong</p><p className="mt-0.5 text-xs text-red-300/80">{error?.message || String(error)}</p></div>
    {retry && <button className="btn-ghost" onClick={retry}>Retry</button>}
  </div>
);

export const Note = ({ tone = 'info', children, className = '' }) => {
  const m = { info: ['border-brand-500/30 bg-brand-500/5 text-ink-200', Info, 'text-brand-400'], warn: ['border-amber-500/30 bg-amber-500/5 text-amber-100', AlertTriangle, 'text-amber-400'], good: ['border-teal-500/30 bg-teal-500/5 text-teal-100', CheckCircle2, 'text-teal-400'] }[tone];
  const Icon = m[1];
  return <div className={`flex gap-2.5 rounded-lg border p-3 text-xs leading-relaxed ${m[0]} ${className}`}><Icon size={15} className={`mt-0.5 shrink-0 ${m[2]}`} /><div>{children}</div></div>;
};

export function Field({ label, hint, error, children }) {
  return <label className="block"><span className="label">{label}</span>{children}{hint && !error && <span className="mt-1 block text-[11px] text-ink-400">{hint}</span>}{error && <span className="mt-1 block text-[11px] text-red-300">{error}</span>}</label>;
}

export function Modal({ title, onClose, children, footer, wide }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`fade-in max-h-[90vh] w-full overflow-hidden rounded-xl border border-ink-600 bg-ink-850 shadow-2xl ${wide ? 'max-w-3xl' : 'max-w-lg'}`}>
        <div className="flex items-center justify-between border-b border-ink-700 px-4 py-3"><h2 className="text-sm font-semibold text-white">{title}</h2><button onClick={onClose} className="rounded p-1 text-ink-300 hover:bg-ink-700" aria-label="Close"><X size={16} /></button></div>
        <div className="max-h-[70vh] overflow-y-auto p-4">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-ink-700 px-4 py-3">{footer}</div>}
      </div>
    </div>
  );
}

export function Confirm({ title, text, confirmLabel = 'Confirm', danger, onConfirm, onClose }) {
  return <Modal title={title} onClose={onClose} footer={<><button className="btn-ghost" onClick={onClose}>Cancel</button><button className={danger ? 'btn-danger' : 'btn-primary'} onClick={() => { onConfirm(); onClose(); }}>{confirmLabel}</button></>}><p className="text-sm text-ink-200">{text}</p></Modal>;
}

export const Table = ({ children }) => <div className="overflow-x-auto"><table className="w-full min-w-[560px] border-collapse">{children}</table></div>;
export const THead = ({ cols }) => <thead><tr className="border-b border-ink-700">{cols.map((c) => <th key={c} className="th">{c}</th>)}</tr></thead>;

export function Tabs({ tabs, value, onChange }) {
  return (
    <div className="mb-4 flex gap-1 overflow-x-auto rounded-lg border border-ink-700 bg-ink-900 p-1" role="tablist">
      {tabs.map((t) => <button key={t.id} role="tab" aria-selected={value === t.id} onClick={() => onChange(t.id)} className={`whitespace-nowrap rounded-md px-3 py-1.5 text-sm transition-colors ${value === t.id ? 'bg-ink-700 text-white' : 'text-ink-300 hover:text-white'}`}>{t.label}</button>)}
    </div>
  );
}

const ToastCtx = createContext(null);
export const useToast = () => useContext(ToastCtx);
export function ToastProvider({ children }) {
  const [items, setItems] = useState([]);
  const push = useCallback((kind, msg) => {
    const id = Math.random().toString(36).slice(2);
    setItems((x) => [...x, { id, kind, msg }]);
    setTimeout(() => setItems((x) => x.filter((i) => i.id !== id)), kind === 'err' ? 7000 : 3500);
  }, []);
  const api = { ok: (m) => push('ok', m), err: (m) => push('err', m) };
  return (
    <ToastCtx.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(92vw,380px)] flex-col gap-2" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`fade-in pointer-events-auto flex items-start gap-2 rounded-lg border p-3 text-sm shadow-xl ${t.kind === 'ok' ? 'border-teal-500/40 bg-ink-800 text-teal-100' : 'border-red-500/40 bg-ink-800 text-red-100'}`}>
            {t.kind === 'ok' ? <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-teal-400" /> : <XCircle size={16} className="mt-0.5 shrink-0 text-red-400" />}<span>{t.msg}</span>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

export const Loadable = ({ state, children }) => state.loading && !state.data ? <Spinner /> : state.error ? <ErrorBox error={state.error} retry={state.reload} /> : children(state.data);
