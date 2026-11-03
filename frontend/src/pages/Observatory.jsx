import { useEffect, useMemo, useState } from 'react';
import { ChevronsLeft, ChevronsRight, Pause, Play, SkipBack, SkipForward, Zap } from 'lucide-react';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import FlowGraph from '../components/FlowGraph.jsx';
import { Badge, Card, CardHeader, Empty, Field, Note, PageHeader, Spinner, useToast } from '../components/ui.jsx';
import { fmtNs, frameState, num } from '../lib/util.js';

const PSEUDO = [
  'flow ← 0',
  'repeat',
  '  BFS from s over arcs with residual > 0; record parent arc',
  '  if t was not reached: stop            // reached set = min cut side S',
  '  b ← min residual capacity along the s→t path',
  '  for each arc on the path: res[e] −= b ; res[reverse(e)] += b',
  '  flow ← flow + b',
  'return flow and the cut (S, V∖S)',
];
const ACTIVE = { initial: [0], bfs: [1, 2], path: [3, 4], done: [5, 6], final: [1, 2, 3, 7] };

export default function Observatory() {
  const presets = useApi(() => api.get('/observatory/presets'));
  const toast = useToast(); const [run, busy] = useAction(toast);
  const [source, setSource] = useState('PRESET:greedy-trap'); const [maxTeams, setMaxTeams] = useState(12);
  const [data, setData] = useState(null);
  const [frame, setFrame] = useState(0); const [phase, setPhase] = useState('done'); const [vc, setVc] = useState(0);
  const [playing, setPlaying] = useState(false); const [speed, setSpeed] = useState(350);
  const [showIsolated, setShowIsolated] = useState(false); const [showResidual, setShowResidual] = useState(true);

  const o = data?.outcome; const steps = o?.steps || []; const total = steps.length + 2;
  const st = o ? frameState(o.graph, steps, o.terminal, frame) : null;
  const visitLen = st?.kind === 'augment' ? st.step.visitOrder.length : 0;

  const load = () => run(async () => {
    const r = await api.post('/observatory/trace', { source, maxTeams: source === 'LIVE' ? Number(maxTeams) : null });
    setData(r); setFrame(0); setPhase('done'); setVc(0); setPlaying(false);
  });

  const goto = (f) => { setPlaying(false); setFrame(Math.max(0, Math.min(total - 1, f))); setPhase('done'); setVc(0); };
  useEffect(() => {
    if (!playing || !o) return undefined;
    const t = setTimeout(() => {
      if (st.kind === 'augment' && phase === 'bfs') { if (vc < visitLen) setVc(vc + 1); else setPhase('path'); }
      else if (st.kind === 'augment' && phase === 'path') setPhase('done');
      else if (frame < total - 1) { const nk = frame + 1 <= steps.length ? 'bfs' : 'done'; setFrame(frame + 1); setPhase(nk); setVc(0); }
      else setPlaying(false);
    }, speed);
    return () => clearTimeout(t);
  }, [playing, frame, phase, vc, o, speed]); // eslint-disable-line react-hooks/exhaustive-deps

  const label = (id) => o.graph.nodes[id].label;
  const activeLines = useMemo(() => new Set(st ? ACTIVE[st.kind === 'augment' ? phase : st.kind] : []), [st, phase]);
  const reverseUsed = st?.step ? st.step.pathEdges.filter((_, i) => st.step.pathReverse[i]).length : 0;

  return (
    <>
      <PageHeader title="Algorithm observatory" subtitle="Step through the real Edmonds–Karp execution. The Java backend returns the BFS visit order, every augmenting path, its bottleneck and the flow after each step — the browser only replays that trace." />
      <Card className="mb-6">
        <div className="flex flex-wrap items-end gap-3 p-4">
          <div className="min-w-[260px] flex-1"><Field label="Instance"><select className="input" value={source} onChange={(e) => setSource(e.target.value)}>
            {(presets.data || []).map((p) => <option key={p.id} value={`PRESET:${p.id}`}>{p.title} — {p.teams} teams</option>)}<option value="LIVE">Live campus data (first N teams)</option></select></Field></div>
          {source === 'LIVE' && <div className="w-32"><Field label="Teams (N)" hint="max 200"><input type="number" min="1" max="200" className="input" value={maxTeams} onChange={(e) => setMaxTeams(e.target.value)} /></Field></div>}
          <button className="btn-primary" disabled={busy} onClick={load}><Zap size={16} />Execute in backend</button>
        </div>
        {source !== 'LIVE' && presets.data && <p className="px-4 pb-4 text-xs text-ink-300">{presets.data.find((p) => `PRESET:${p.id}` === source)?.description}</p>}
        {source === 'LIVE' && <p className="px-4 pb-4 text-xs text-ink-300">Runs the real network built from the current database, restricted to the first N teams so the picture stays readable (the trace is limited to 400 nodes).</p>}
      </Card>

      {busy && !data && <Spinner label="Running Edmonds–Karp on the server…" />}
      {!o && !busy && <Card><Empty title="Choose an instance and execute" text="Try the “Greedy trap” preset: you will see an augmenting path that uses a reverse residual edge to re-route a team." /></Card>}
      {o && (
        <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
          <div className="min-w-0 space-y-3">
            <Card>
              <div className="flex flex-wrap items-center gap-2 border-b border-ink-700 p-3">
                <button className="btn-ghost !px-2" onClick={() => goto(0)} aria-label="First frame"><SkipBack size={15} /></button>
                <button className="btn-ghost !px-2" onClick={() => goto(frame - 1)} aria-label="Previous frame"><ChevronsLeft size={15} /></button>
                <button className="btn-primary !px-3" onClick={() => { if (!playing && frame >= total - 1) { setFrame(0); setPhase('done'); } setPlaying(!playing); }}>{playing ? <Pause size={15} /> : <Play size={15} />}{playing ? 'Pause' : 'Play'}</button>
                <button className="btn-ghost !px-2" onClick={() => goto(frame + 1)} aria-label="Next frame"><ChevronsRight size={15} /></button>
                <button className="btn-ghost !px-2" onClick={() => goto(total - 1)} aria-label="Last frame"><SkipForward size={15} /></button>
                <input type="range" min="0" max={total - 1} value={frame} onChange={(e) => goto(Number(e.target.value))} className="mx-2 min-w-[120px] flex-1 accent-blue-500" aria-label="Frame" />
                <label className="flex items-center gap-2 text-xs text-ink-300">Speed<input type="range" min="60" max="900" step="30" value={960 - speed} onChange={(e) => setSpeed(960 - Number(e.target.value))} className="w-20 accent-teal-400" /></label>
              </div>
              <div className="flex flex-wrap items-center gap-4 px-3 py-2 text-xs text-ink-300">
                <Badge tone="brand">{st.kind === 'initial' ? 'Initial network' : st.kind === 'final' ? 'Final state' : `Augmentation ${frame} of ${steps.length}`}</Badge>
                <span>Flow value: <b className="text-white">{st.flowValue}</b></span>
                {st.kind === 'augment' && <span>Phase: <b className="text-white">{phase === 'bfs' ? 'BFS search' : phase === 'path' ? 'augmenting path found' : 'flow updated'}</b></span>}
                <label className="ml-auto flex items-center gap-1.5"><input type="checkbox" checked={showResidual} onChange={(e) => setShowResidual(e.target.checked)} />residual reverse arcs</label>
                <label className="flex items-center gap-1.5"><input type="checkbox" checked={showIsolated} onChange={(e) => setShowIsolated(e.target.checked)} />unconnected slots</label>
              </div>
            </Card>
            <FlowGraph graph={o.graph} steps={steps} terminal={o.terminal} frame={frame} phase={phase} visitedCount={vc} showIsolated={showIsolated} showResidual={showResidual} />
            <div className="flex flex-wrap gap-4 text-[11px] text-ink-300">
              {[['#60a5fa', 'augmenting path'], ['#2dd4bf', 'carries flow (flow/capacity)'], ['#fbbf24', 'residual reverse arc'], ['#f87171', 'minimum-cut edge'], ['#3b82f6', 'BFS-visited / source side S']].map(([c, t]) => <span key={t} className="flex items-center gap-1.5"><span className="h-2 w-4 rounded" style={{ background: c }} />{t}</span>)}
            </div>
            {st.kind === 'final' && (
              <Card><CardHeader title="Maximum flow & minimum cut" hint={`Max flow = ${o.allocated} = min-cut capacity ${o.bottleneck.minCutValue}`} />
                <div className="space-y-2 p-4">{o.bottleneck.lines.map((l, i) => <p key={i} className="text-sm leading-relaxed text-ink-200">{l}</p>)}
                  {o.bottleneck.slots.length > 0 && <div className="flex flex-wrap gap-1.5 pt-1">{o.bottleneck.slots.map((s) => <Badge key={s.lab + s.slot} tone="bad">{s.lab} / {s.slot} · cap {s.capacity}</Badge>)}</div>}
                  {o.unallocated.length > 0 && <Note tone="warn" className="mt-2">Unallocated: {o.unallocated.map((u) => `${u.team} (${u.category.replace('_', ' ').toLowerCase()})`).join(', ')}</Note>}</div></Card>)}
          </div>
          <div className="space-y-6">
            <Card><CardHeader title={st.kind === 'augment' ? `Augmentation ${st.step.iteration}` : 'Step details'} />
              <div className="space-y-2.5 p-4 text-sm">
                {st.kind === 'augment' ? <>
                  <div><span className="label">Augmenting path (shortest, by BFS)</span><p className="font-mono text-xs leading-relaxed text-ink-100">{st.step.pathNodes.map(label).join(' → ')}</p></div>
                  <div className="flex gap-6"><div><span className="label">Bottleneck</span><span className="text-xl font-semibold text-brand-400">{st.step.bottleneck}</span></div><div><span className="label">Path length</span><span className="text-xl font-semibold text-white">{st.step.pathEdges.length}</span></div><div><span className="label">Reverse arcs used</span><span className="text-xl font-semibold text-amber-300">{reverseUsed}</span></div></div>
                  <div><span className="label">BFS visit order ({st.step.visitOrder.length} nodes)</span><p className="max-h-20 overflow-y-auto font-mono text-[11px] text-ink-300">{st.step.visitOrder.map(label).join(', ')}</p></div>
                  {reverseUsed > 0 && <Note tone="warn">This path cancels earlier flow through a reverse residual arc, re-routing a team to make room.</Note>}
                </> : st.kind === 'initial' ? <p className="text-xs text-ink-300">All flows are zero. Source edges have capacity 1 per team; slot→sink edges have the laboratory capacity.</p>
                  : <p className="text-xs text-ink-300">The final BFS reached {o.terminal.sourceSide.length} node(s) and could not reach the sink, so the flow is maximum.</p>}
              </div></Card>
            <Card><CardHeader title="Pseudocode" hint="Edmonds–Karp" />
              <pre className="overflow-x-auto p-3 font-mono text-[11.5px] leading-6">{PSEUDO.map((l, i) => <div key={i} className={`whitespace-pre rounded px-2 ${activeLines.has(i) ? 'bg-brand-500/15 text-white' : 'text-ink-300'}`}><span className="mr-2 inline-block w-3 text-ink-500">{i + 1}</span>{l}</div>)}</pre></Card>
            <Card><CardHeader title="Execution statistics" hint="Measured in Java" />
              <dl className="grid grid-cols-2 gap-px bg-ink-700 text-center">{[['Nodes', o.stats.nodes], ['Edges', o.stats.edges], ['BFS runs', o.stats.bfsRuns], ['Augmentations', o.stats.augmentations], ['Nodes visited', o.stats.nodesVisited], ['Edges scanned', o.stats.edgesScanned], ['Flow time', fmtNs(o.stats.flowNanos)], ['Build time', fmtNs(o.stats.buildNanos)]].map(([k, v]) => <div key={k} className="bg-ink-850 p-2.5"><dd className="text-base font-semibold tabular-nums text-white">{typeof v === 'number' ? num(v) : v}</dd><dt className="text-[11px] text-ink-300">{k}</dt></div>)}</dl></Card>
            {data.greedy && <Card><CardHeader title="Greedy baseline on this instance" /><div className="space-y-1 p-4 text-xs text-ink-200">{data.greedy.map((g) => <div key={g.order} className="flex justify-between"><span>{g.order.replace(/_/g, ' ').toLowerCase()}</span><b className={g.allocated < o.allocated ? 'text-amber-300' : 'text-teal-300'}>{g.allocated} / {o.totalTeams}</b></div>)}<p className="pt-2 text-ink-400">Edmonds–Karp: {o.allocated} / {o.totalTeams}</p></div></Card>}
          </div>
        </div>)}
    </>
  );
}
