import { useCallback, useEffect, useRef, useState } from 'react';

// Each read owns its cancellation scope; an old server/filter response cannot win.
export function useResource(fetcher, base, { enabled = true, query = '' } = {}) {
  const [state, setState] = useState({ status: 'idle', data: null });
  const active = useRef(null);
  const reload = useCallback(async () => {
    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    setState({ status: 'loading', data: null });
    try {
      const data = await fetcher(base, controller.signal, query);
      if (controller.signal.aborted) return { ok: false, cancelled: true };
      setState({ status: 'ok', data, at: new Date() });
      return { ok: true, data };
    } catch (error) {
      if (controller.signal.aborted) return { ok: false, cancelled: true };
      setState({ status: 'error', data: null, error: error.message });
      return { ok: false, error: error.message };
    }
  }, [fetcher, base, query]);
  useEffect(() => {
    if (enabled) reload();
    return () => active.current?.abort();
  }, [enabled, reload]);
  return { ...state, reload };
}
