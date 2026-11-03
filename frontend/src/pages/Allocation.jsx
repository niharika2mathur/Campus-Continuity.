import { useRef, useState } from 'react';
import { Download, FileUp, Play, Upload } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Badge, Card, CardHeader, Empty, Field, Loadable, Note, PageHeader, Stat, Table, Tabs, THead, useToast } from '../components/ui.jsx';
import { download, fmtNs, pct, when } from '../lib/util.js';

const COLS = { labs: 'code, name, building, capacity, max_team_size, equipment, status, reason', slots: 'code, label, day, start, duration_minutes, status', teams: 'request_id, code, name, size, requirements, duration_minutes, eligible_slots' };
const tone = { VALID: 'good', INVALID: 'bad', DUPLICATE: 'warn' };

export default function Allocation() {
  const toast = useToast(); const [run, busy] = useAction(toast);
  const fileRef = useRef(null);
  const [type, setType] = useState('labs'); const [file, setFile] = useState(null); const [preview, setPreview] = useState(null); const [committed, setCommitted] = useState(null);
  const [name, setName] = useState(''); const [result, setResult] = useState(null); const [tab, setTab] = useState('assign');
  const history = useApi(() => api.get('/allocations'));

  const doPreview = () => run(async () => { setCommitted(null); setPreview(await api.upload(`/import/${type}/preview`, file)); });
  const doCommit = () => run(async () => { const r = await api.upload(`/import/${type}/commit`, file); setCommitted(r); setPreview(null); setFile(null); if (fileRef.current) fileRef.current.value = ''; }, 'Import committed');
  const doRun = () => run(async () => { const r = await api.post('/allocations', { scenarioName: name, includeTrace: false }); setResult(r); history.reload(); setTab('assign'); }, 'Allocation computed by Edmonds–Karp');
  const open = (id) => run(async () => setResult({ ...(await api.get(`/allocations/${id}`)), id }));
  const exportCsv = () => run(async () => download(`allocation-${result.id}.csv`, await api.text(`/allocations/${result.id}/export`)));

  const o = result?.outcome;
  return (
    <>
      <PageHeader title="Allocation studio" subtitle="Import data, validate it, then execute Edmonds–Karp in the Java backend to find the maximum feasible number of team placements." />
      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader title="1 · Import & validate CSV" hint="Preview never writes; commit imports valid rows only" icon={FileUp} />
          <div className="space-y-3 p-4">
            <div className="grid gap-3 sm:grid-cols-[160px_1fr]">
              <Field label="Data type"><select className="input" value={type} onChange={(e) => { setType(e.target.value); setPreview(null); setCommitted(null); }}><option value="labs">Laboratories</option><option value="slots">Time slots</option><option value="teams">Team requests</option></select></Field>
              <Field label="CSV file" hint={`Columns: ${COLS[type]}`}><input ref={fileRef} type="file" accept=".csv,text/csv" className="input file:mr-3 file:rounded file:border-0 file:bg-ink-700 file:px-2 file:py-1 file:text-xs file:text-ink-100" onChange={(e) => { setFile(e.target.files[0] || null); setPreview(null); setCommitted(null); }} /></Field>
            </div>
            <div className="flex gap-2"><button className="btn-ghost" disabled={!file || busy} onClick={doPreview}><Upload size={15} />Validate &amp; preview</button><button className="btn-primary" disabled={!preview?.valid || busy || preview?.fileErrors?.length} onClick={doCommit}>Import {preview?.valid ?? 0} valid row(s)</button></div>
            <Note>Import order matters: laboratories and time slots first, then team requests. Team rows pass through the Bloom filter and exact duplicate check.</Note>
            {preview && (
              <div className="fade-in space-y-2">
                {preview.fileErrors.map((e) => <Note key={e} tone="warn">{e}</Note>)}
                {!preview.fileErrors.length && <div className="flex gap-2"><Badge tone="good">{preview.valid} valid</Badge><Badge tone="bad">{preview.invalid} invalid</Badge><Badge tone="warn">{preview.duplicate} duplicate</Badge></div>}
                {preview.rows.length > 0 && <div className="max-h-64 overflow-auto rounded-lg border border-ink-700"><Table><THead cols={['Line', 'Status', 'Code', 'Problems']} /><tbody>{preview.rows.slice(0, 200).map((r) => <tr key={r.line} className="border-b border-ink-700/50"><td className="td text-xs">{r.line}</td><td className="td"><Badge tone={tone[r.status]}>{r.status}</Badge></td><td className="td font-mono text-xs">{r.values.code}</td><td className="td text-xs text-ink-300">{r.errors.join('; ') || '—'}</td></tr>)}</tbody></Table></div>}
              </div>)}
            {committed && <Note tone="good">Imported {committed.created} {committed.type}. Skipped: {committed.skippedDuplicates} duplicates, {committed.skippedInvalid} invalid{committed.type === 'teams' ? `, ${committed.bloomRejected} Bloom-screened duplicates` : ''}.</Note>}
          </div>
        </Card>
        <Card>
          <CardHeader title="2 · Configure & execute" hint="Objective: maximum number of placed teams" icon={Play} />
          <div className="space-y-3 p-4">
            <Field label="Scenario name" hint="Optional label stored in the run history"><input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Baseline term 1" /></Field>
            <button className="btn-primary w-full" disabled={busy} onClick={doRun}><Play size={16} />Run Edmonds–Karp on current data</button>
            <Note>Each run rebuilds the flow network from the database, so results always reflect the current inventory. Edmonds–Karp maximises the <b>number</b> of allocated teams; it does not optimise preferences or travel distance.</Note>
            <div><span className="label">Recent runs</span><Loadable state={history}>{(h) => h.length === 0 ? <p className="text-xs text-ink-400">No runs yet.</p> : <ul className="max-h-40 space-y-1 overflow-y-auto">{h.slice(0, 8).map((r) => <li key={r.id}><button className="w-full rounded-lg px-2 py-1.5 text-left text-xs hover:bg-ink-800" onClick={() => open(r.id)}><span className="text-white">{r.name}</span> <span className="text-ink-400">· {r.summary} · {when(r.createdAt)}</span></button></li>)}</ul>}</Loadable></div>
          </div>
        </Card>
      </div>

      {o && (
        <div className="fade-in mt-6 space-y-6">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-6">
            <Stat label="Teams" value={o.totalTeams} /><Stat label="Allocated" value={o.allocated} tone="good" /><Stat label="Unallocated" value={o.unallocatedCount} tone={o.unallocatedCount ? 'bad' : 'good'} />
            <Stat label="Utilisation" value={pct(o.utilization)} tone="brand" sub={`${o.usedSeats}/${o.totalSeats} seats`} /><Stat label="Flow time" value={fmtNs(o.stats.flowNanos)} sub={`${o.stats.augmentations} augmentations`} /><Stat label="Greedy (input order)" value={result.greedy[0].allocated} tone="warn" sub={`${o.allocated - result.greedy[0].allocated} fewer`} />
          </div>
          <Card>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-ink-700 px-4 py-3"><h3 className="text-sm font-semibold">Result · {result.scenarioName}</h3>{result.id && <button className="btn-ghost" onClick={exportCsv}><Download size={15} />Export CSV</button>}</div>
            <div className="p-4"><Tabs tabs={[{ id: 'assign', label: `Assignments (${o.assignments.length})` }, { id: 'un', label: `Unallocated (${o.unallocated.length})` }, { id: 'util', label: 'Capacity utilisation' }, { id: 'greedy', label: 'Baseline comparison' }]} value={tab} onChange={setTab} />
              {tab === 'assign' && (o.assignments.length === 0 ? <Empty title="No assignments" text="No team could be placed." /> : <div className="max-h-96 overflow-auto"><Table><THead cols={['Team', 'Laboratory', 'Slot']} /><tbody>{o.assignments.map((a) => <tr key={a.team} className="border-b border-ink-700/50"><td className="td">{a.team}</td><td className="td">{a.lab}</td><td className="td">{a.slot}</td></tr>)}</tbody></Table></div>)}
              {tab === 'un' && (o.unallocated.length === 0 ? <Empty title="Every team is allocated" /> : <div className="max-h-96 overflow-auto"><Table><THead cols={['Team', 'Category', 'Reason']} /><tbody>{o.unallocated.map((u) => <tr key={u.team} className="border-b border-ink-700/50"><td className="td">{u.team}</td><td className="td"><Badge tone={u.category === 'CAPACITY_EXHAUSTED' ? 'warn' : 'bad'}>{u.category.replace(/_/g, ' ')}</Badge></td><td className="td text-xs text-ink-300">{u.detail}</td></tr>)}</tbody></Table></div>)}
              {tab === 'util' && <div className="grid gap-4 lg:grid-cols-2"><div className="h-64"><ResponsiveContainer width="100%" height="100%"><BarChart data={o.labLoads}><CartesianGrid stroke="#26334f" strokeDasharray="3 3" vertical={false} /><XAxis dataKey="lab" stroke="#94a3bd" fontSize={11} /><YAxis stroke="#94a3bd" fontSize={11} /><Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #26334f', borderRadius: 8, fontSize: 12 }} /><Bar dataKey="used" name="Used" fill="#2dd4bf" radius={[4, 4, 0, 0]} /><Bar dataKey="seats" name="Seats" fill="#3b82f6" fillOpacity={0.4} radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div>
                <div className="max-h-64 overflow-auto"><Table><THead cols={['Lab / slot', 'Used', 'Capacity']} /><tbody>{o.slotLoads.filter((s) => s.used > 0).map((s) => <tr key={s.lab + s.slot} className="border-b border-ink-700/50"><td className="td text-xs">{s.lab} / {s.slot}</td><td className="td tabular-nums">{s.used}</td><td className="td tabular-nums">{s.capacity}</td></tr>)}</tbody></Table></div></div>}
              {tab === 'greedy' && <div className="space-y-3"><Table><THead cols={['Method', 'Placed', 'Unplaced', 'Time', 'Compat. checks']} /><tbody><tr className="border-b border-ink-700/50"><td className="td font-medium text-teal-300">Edmonds–Karp (optimal)</td><td className="td tabular-nums">{o.allocated}</td><td className="td tabular-nums">{o.unallocatedCount}</td><td className="td">{fmtNs(o.stats.flowNanos)}</td><td className="td text-xs text-ink-400">{o.stats.edgesScanned.toLocaleString()} edge scans</td></tr>{result.greedy.map((g) => <tr key={g.order} className="border-b border-ink-700/50"><td className="td">Greedy · {g.order.replace(/_/g, ' ').toLowerCase()}</td><td className="td tabular-nums">{g.allocated}</td><td className="td tabular-nums">{g.unallocated}</td><td className="td">{fmtNs(g.nanos)}</td><td className="td tabular-nums text-xs text-ink-400">{g.compatChecks.toLocaleString()}</td></tr>)}</tbody></Table>{result.comparison.map((c, i) => <p key={i} className="text-xs leading-relaxed text-ink-200">{c}</p>)}</div>}
            </div>
          </Card>
        </div>)}
    </>
  );
}
