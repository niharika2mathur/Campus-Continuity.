import { useState } from 'react';
import { BarChart3, Download, Play } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Card, CardHeader, Empty, Field, Note, PageHeader, Table, THead, useToast } from '../components/ui.jsx';
import { download, fmtMs, num, pct, toCsv, when } from '../lib/util.js';

const SIZES = [100, 250, 500, 1000, 1500, 2000];
const tip = { contentStyle: { background: '#0f172a', border: '1px solid #26334f', borderRadius: 8, fontSize: 12 } };
const axis = { stroke: '#94a3bd', fontSize: 11 };

/** Empirical log-log slope between consecutive sizes: how time actually grew, to compare with the theoretical bound. */
const slopes = (rows, key) => rows.slice(1).map((r, i) => ({ from: rows[i].teams, to: r.teams, k: Math.log(r[key] / rows[i][key]) / Math.log(r.teams / rows[i].teams) }));

export default function Benchmarks() {
  const toast = useToast(); const [run, busy] = useAction(toast);
  const history = useApi(() => api.get('/benchmarks'));
  const [sizes, setSizes] = useState([100, 500, 1000]); const [reps, setReps] = useState(3); const [seed, setSeed] = useState(42); const [fpp, setFpp] = useState(0.01); const [log, setLog] = useState(false);
  const [data, setData] = useState(null);
  const toggle = (s) => setSizes(sizes.includes(s) ? sizes.filter((x) => x !== s) : [...sizes, s].sort((a, b) => a - b));
  const exec = () => run(async () => { setData(await api.post('/benchmarks', { sizes, repetitions: Number(reps), seed: Number(seed), falsePositiveProbability: Number(fpp) })); history.reload(); }, 'Benchmark finished');
  const open = (id) => run(async () => setData(await api.get(`/benchmarks/${id}`)));

  const a = data?.report?.allocation || []; const b = data?.report?.bloom || [];
  const sl = a.length > 1 ? slopes(a, 'ekFlowMs') : [];
  return (
    <>
      <PageHeader title="Performance & benchmarking" subtitle="Run Edmonds–Karp, the greedy baseline, the Bloom filter and exact lookup on seeded synthetic campuses of increasing size. All timings are measured by Java on the server."
        actions={data && <><button className="btn-ghost" onClick={() => download('benchmark-allocation.csv', toCsv(a))}><Download size={15} />Allocation CSV</button><button className="btn-ghost" onClick={() => download('benchmark-bloom.csv', toCsv(b))}><Download size={15} />Bloom CSV</button><button className="btn-ghost" onClick={() => download('benchmark.json', JSON.stringify(data, null, 2), 'application/json')}><Download size={15} />JSON</button></>} />
      <Card className="mb-6"><CardHeader title="Configuration" icon={BarChart3} />
        <div className="grid gap-4 p-4 md:grid-cols-[1fr_120px_120px_140px_auto] md:items-end">
          <div><span className="label">Dataset sizes (teams)</span><div className="flex flex-wrap gap-2">{SIZES.map((s) => <button key={s} onClick={() => toggle(s)} className={`rounded-lg border px-3 py-1.5 text-sm tabular-nums ${sizes.includes(s) ? 'border-brand-500 bg-brand-500/15 text-white' : 'border-ink-600 text-ink-300 hover:text-white'}`} aria-pressed={sizes.includes(s)}>{s}</button>)}</div></div>
          <Field label="Repetitions"><input type="number" min="1" max="15" className="input" value={reps} onChange={(e) => setReps(e.target.value)} /></Field>
          <Field label="Random seed"><input type="number" className="input" value={seed} onChange={(e) => setSeed(e.target.value)} /></Field>
          <Field label="Bloom target FPP"><input type="number" step="0.005" min="0.0001" max="0.5" className="input" value={fpp} onChange={(e) => setFpp(e.target.value)} /></Field>
          <button className="btn-primary" disabled={busy || sizes.length === 0} onClick={exec}><Play size={15} />{busy ? 'Running…' : 'Run benchmark'}</button>
        </div>
        <div className="px-4 pb-4"><Note tone="warn">Edmonds–Karp is expensive on dense graphs: 2,000 teams took ≈4.5 s on the reference machine, and sizes are capped at 3,000. Pick at least three increasing sizes for the academic evidence.</Note></div>
      </Card>

      {!data ? <Card><Empty title="No benchmark loaded" text="Run a benchmark or open one from history below." /></Card> : (
        <div className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-2">
            <Card><CardHeader title="Allocation time vs dataset size" hint="Median over repetitions" actions={<label className="flex items-center gap-1.5 text-xs text-ink-300"><input type="checkbox" checked={log} onChange={(e) => setLog(e.target.checked)} />log scale</label>} />
              <div className="h-72 p-4"><ResponsiveContainer width="100%" height="100%"><LineChart data={a}><CartesianGrid stroke="#26334f" strokeDasharray="3 3" /><XAxis dataKey="teams" {...axis} /><YAxis {...axis} scale={log ? 'log' : 'auto'} domain={log ? [0.01, 'auto'] : [0, 'auto']} allowDataOverflow /><Tooltip {...tip} formatter={(v) => fmtMs(v)} /><Legend wrapperStyle={{ fontSize: 12 }} />
                <Line type="monotone" dataKey="ekTotalMs" name="Edmonds–Karp (build + flow)" stroke="#3b82f6" strokeWidth={2} dot /><Line type="monotone" dataKey="ekFlowMs" name="Edmonds–Karp (flow only)" stroke="#60a5fa" strokeDasharray="4 3" dot={false} /><Line type="monotone" dataKey="greedyMs" name="Greedy first-fit" stroke="#2dd4bf" strokeWidth={2} dot /></LineChart></ResponsiveContainer></div></Card>
            <Card><CardHeader title="Teams placed" hint="Quality of the result, not speed" />
              <div className="h-72 p-4"><ResponsiveContainer width="100%" height="100%"><BarChart data={a}><CartesianGrid stroke="#26334f" strokeDasharray="3 3" vertical={false} /><XAxis dataKey="teams" {...axis} /><YAxis {...axis} /><Tooltip {...tip} /><Legend wrapperStyle={{ fontSize: 12 }} /><Bar dataKey="ekAllocated" name="Edmonds–Karp" fill="#3b82f6" radius={[4, 4, 0, 0]} /><Bar dataKey="greedyAllocated" name="Greedy (input order)" fill="#2dd4bf" radius={[4, 4, 0, 0]} /><Bar dataKey="greedyWorstOrderAllocated" name="Greedy (worst of 4 orders)" fill="#6b7a99" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div></Card>
            <Card><CardHeader title="Membership lookup time" hint="Same probes (half present, half absent)" />
              <div className="h-72 p-4"><ResponsiveContainer width="100%" height="100%"><LineChart data={b}><CartesianGrid stroke="#26334f" strokeDasharray="3 3" /><XAxis dataKey="keys" {...axis} /><YAxis {...axis} /><Tooltip {...tip} formatter={(v) => fmtMs(v)} /><Legend wrapperStyle={{ fontSize: 12 }} /><Line dataKey="bloomLookupMs" name="Bloom filter only" stroke="#fbbf24" strokeWidth={2} /><Line dataKey="exactLookupMs" name="Exact HashSet" stroke="#2dd4bf" strokeWidth={2} /><Line dataKey="hybridLookupMs" name="Bloom + exact verify" stroke="#60a5fa" strokeWidth={2} /></LineChart></ResponsiveContainer></div></Card>
            <Card><CardHeader title="Bloom false-positive rate" hint="Observed vs configured target" />
              <div className="h-72 p-4"><ResponsiveContainer width="100%" height="100%"><BarChart data={b}><CartesianGrid stroke="#26334f" strokeDasharray="3 3" vertical={false} /><XAxis dataKey="keys" {...axis} /><YAxis {...axis} tickFormatter={(v) => pct(v, 1)} /><Tooltip {...tip} formatter={(v) => pct(v, 2)} /><Legend wrapperStyle={{ fontSize: 12 }} /><Bar dataKey="observedFpp" name="Observed" fill="#fbbf24" radius={[4, 4, 0, 0]} /><Bar dataKey="targetFpp" name="Target" fill="#6b7a99" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div></Card>
          </div>
          <Card><CardHeader title="Allocation measurements" hint={`seed ${data.report.seed}, ${data.report.repetitions} repetitions`} />
            <Table><THead cols={['Teams', 'Nodes', 'Edges', 'EK placed', 'Greedy', 'Worst greedy', 'EK ms', 'Greedy ms', 'BFS runs', 'Edge scans', 'Augment.']} /><tbody>{a.map((r) => <tr key={r.teams} className="border-b border-ink-700/50"><td className="td tabular-nums">{r.teams}</td><td className="td tabular-nums">{num(r.graphNodes)}</td><td className="td tabular-nums">{num(r.graphEdges)}</td><td className="td tabular-nums text-brand-400">{r.ekAllocated}</td><td className="td tabular-nums text-teal-300">{r.greedyAllocated}</td><td className="td tabular-nums">{r.greedyWorstOrderAllocated}</td><td className="td tabular-nums">{r.ekTotalMs.toFixed(1)}</td><td className="td tabular-nums">{r.greedyMs.toFixed(2)}</td><td className="td tabular-nums">{num(r.bfsRuns)}</td><td className="td tabular-nums">{num(r.edgesScanned)}</td><td className="td tabular-nums">{num(r.augmentations)}</td></tr>)}</tbody></Table></Card>
          <Card><CardHeader title="Bloom filter measurements" />
            <Table><THead cols={['Keys', 'Bits', 'k', 'Bloom ms', 'Exact ms', 'Bloom+verify ms', 'False pos.', 'Observed FPR', 'Target', 'False neg.']} /><tbody>{b.map((r) => <tr key={r.keys} className="border-b border-ink-700/50"><td className="td tabular-nums">{r.keys}</td><td className="td tabular-nums">{num(r.bits)}</td><td className="td">{r.hashes}</td><td className="td tabular-nums">{r.bloomLookupMs.toFixed(2)}</td><td className="td tabular-nums">{r.exactLookupMs.toFixed(2)}</td><td className="td tabular-nums">{r.hybridLookupMs.toFixed(2)}</td><td className="td tabular-nums">{r.falsePositives}/{r.absentProbes}</td><td className="td tabular-nums">{pct(r.observedFpp, 2)}</td><td className="td tabular-nums">{pct(r.targetFpp, 2)}</td><td className="td tabular-nums text-teal-300">{r.falseNegatives}</td></tr>)}</tbody></Table></Card>
          <div className="grid gap-6 lg:grid-cols-2">
            <Card><CardHeader title="Theory (worst-case bounds)" /><div className="space-y-2 p-4 text-xs leading-relaxed text-ink-200">
              <p><b>Edmonds–Karp:</b> O(V·E²) time, O(V+E) space. At most O(V·E) augmentations, each with an O(E) BFS. In this problem every augmentation raises the flow by 1, so augmentations ≤ number of teams.</p>
              <p><b>Greedy first-fit:</b> O(T·S) compatibility checks, O(T+S) space. No optimality guarantee.</p>
              <p><b>Bloom filter:</b> O(k) per insert/query, O(m) space with m = −n·ln p / (ln 2)². Exact HashSet: O(1) expected, O(n·key size) space.</p></div></Card>
            <Card><CardHeader title="Empirical observations (this run)" /><div className="space-y-2 p-4 text-xs leading-relaxed text-ink-200">
              {sl.length ? sl.map((s) => <p key={s.to}>Flow-time growth {s.from} → {s.to} teams: log-log slope ≈ <b className="text-white">{s.k.toFixed(2)}</b> (time ∝ n^{s.k.toFixed(1)}).</p>) : <p>Run at least two sizes to estimate growth.</p>}
              {a.length > 0 && <p>At the largest size Edmonds–Karp placed <b className="text-white">{a[a.length - 1].ekAllocated - a[a.length - 1].greedyAllocated}</b> more teams than input-order greedy, at {(a[a.length - 1].ekTotalMs / Math.max(a[a.length - 1].greedyMs, 1e-6)).toFixed(0)}× the running time.</p>}
              {b.length > 0 && b.every((x) => x.exactLookupMs <= x.bloomLookupMs) && <p>The in-memory HashSet was <b className="text-white">faster</b> than the Bloom filter on every size. A Bloom filter is not a speed-up here; it pays off when the authoritative store is slow (e.g. a database round-trip) or too large for memory, since definite negatives skip that lookup.</p>}
              {data.notes.map((n, i) => <p key={i} className="text-ink-400">{n}</p>)}</div></Card>
          </div>
        </div>)}
      <Card className="mt-6"><CardHeader title="History" />{!history.data?.length ? <Empty title="No benchmark runs yet" /> : <ul className="divide-y divide-ink-700">{history.data.slice(0, 10).map((h) => <li key={h.id}><button className="flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left hover:bg-ink-800/50" onClick={() => open(h.id)}><span className="text-sm text-white">{h.summary}</span><span className="text-[11px] text-ink-400">{when(h.createdAt)}</span></button></li>)}</ul>}</Card>
    </>
  );
}
