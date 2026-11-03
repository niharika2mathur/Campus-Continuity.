import { useCallback, useEffect, useRef, useState } from 'react';

/** Loads data on mount / when deps change; exposes reload(). */
export function useApi(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const alive = useRef(true);
  const run = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try { const data = await fn(); if (alive.current) setState({ data, loading: false, error: null }); }
    catch (error) { if (alive.current) setState({ data: null, loading: false, error }); }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { alive.current = true; run(); return () => { alive.current = false; }; }, [run]);
  return { ...state, reload: run };
}

/** Wraps an async action with busy/error state and toast feedback. */
export function useAction(toast) {
  const [busy, setBusy] = useState(false);
  const run = async (fn, successMsg) => {
    setBusy(true);
    try { const r = await fn(); if (successMsg) toast.ok(successMsg); return r; }
    catch (e) { toast.err(e.message); return undefined; }
    finally { setBusy(false); }
  };
  return [run, busy];
}
