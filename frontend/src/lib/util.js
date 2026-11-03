// Pure helpers (unit-tested in util.test.js). No algorithm logic lives here: the frontend only DISPLAYS backend results.

export const fmtMs = (ms) => (ms == null ? '—' : ms < 1 ? `${ms.toFixed(3)} ms` : ms < 1000 ? `${ms.toFixed(1)} ms` : `${(ms / 1000).toFixed(2)} s`);
export const fmtNs = (ns) => fmtMs(ns / 1e6);
export const pct = (x, d = 1) => (x == null || Number.isNaN(x) ? '—' : `${(x * 100).toFixed(d)}%`);
export const num = (x) => (x == null ? '—' : Number(x).toLocaleString('en-US'));
export const when = (iso) => (iso ? new Date(iso).toLocaleString() : '—');

/** CSV with RFC-4180 quoting and neutralisation of spreadsheet formula injection (=, +, -, @ prefixes). */
export function toCsv(rows, columns) {
  const cols = columns || (rows[0] ? Object.keys(rows[0]) : []);
  const esc = (v) => {
    let s = v == null ? '' : Array.isArray(v) ? v.join(';') : String(v);
    if (/^[=+\-@]/.test(s)) s = `'${s}`;
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return [cols.join(','), ...rows.map((r) => cols.map((c) => esc(r[c])).join(','))].join('\n');
}

export function download(filename, text, mime = 'text/csv') {
  const url = URL.createObjectURL(new Blob([text], { type: `${mime};charset=utf-8` }));
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Layered layout for the flow network: source | teams | lab-slot nodes | sink. Returns {pos, width, height, hidden}. */
export function layoutGraph(graph, { hideIsolated = true } = {}) {
  const teams = graph.nodes.filter((n) => n.type === 'TEAM');
  let slots = graph.nodes.filter((n) => n.type === 'SLOT');
  const hidden = new Set();
  if (hideIsolated) {
    slots = slots.filter((n) => {
      const keep = graph.edges.some((e) => e.to === n.id && e.from !== graph.source);
      if (!keep) hidden.add(n.id);
      return keep;
    });
  }
  const rowH = 26, top = 40;
  const rows = Math.max(teams.length, slots.length, 1);
  const height = top * 2 + rows * rowH;
  const pos = {};
  const place = (list, x) => list.forEach((n, i) => { pos[n.id] = { x, y: top + (i + 0.5) * ((height - top * 2) / list.length) }; });
  place(teams, 300); place(slots, 640);
  pos[graph.source] = { x: 60, y: height / 2 };
  pos[graph.sink] = { x: 900, y: height / 2 };
  return { pos, width: 960, height, hidden };
}

/**
 * Timeline of frames for the Observatory: frame 0 = initial network, frame k (1..n) = after augmentation k,
 * frame n+1 = terminal state (final failing BFS, max flow, min cut). Everything is read from the backend trace.
 */
export function frameState(graph, steps, terminal, frame) {
  const n = steps.length;
  const zero = new Array(graph.edges.length).fill(0);
  if (frame <= 0) return { kind: 'initial', flows: zero, prevFlows: zero, step: null, flowValue: 0 };
  if (frame <= n) {
    const s = steps[frame - 1];
    const prev = frame === 1 ? zero : steps[frame - 2].edgeFlows;
    return { kind: 'augment', flows: s.edgeFlows, prevFlows: prev, step: s, flowValue: s.flowValue };
  }
  return { kind: 'final', flows: terminal.edgeFlows, prevFlows: terminal.edgeFlows, step: null, flowValue: terminal.maxFlow };
}

/** Edges that carry a reverse (residual) arc at the given flows: forward edges with flow > 0. */
export const residualReverse = (flows) => flows.map((f, i) => (f > 0 ? i : -1)).filter((i) => i >= 0);
export const slotKey = (lab, slot) => `${lab} / ${slot}`;

export const AVAIL_STYLE = {
  AVAILABLE: 'bg-teal-500/10 text-teal-300 border-teal-500/30',
  DISRUPTED: 'bg-amber-500/10 text-amber-300 border-amber-500/30',
  DEACTIVATED: 'bg-ink-600/40 text-ink-300 border-ink-500/50',
};
