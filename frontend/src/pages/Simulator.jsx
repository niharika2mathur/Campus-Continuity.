import { useState } from 'react';
import { ArrowRight, FlaskConical, GitCompare, Plus, RotateCcw, Trash2, UploadCloud } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Badge, Card, CardHeader, Confirm, Empty, Field, Note, PageHeader, Stat, Tabs, useToast } from '../components/ui.jsx';
import { pct, when } from '../lib/util.js';

const TYPES = [
  ['DISABLE_LAB', 'Disable a laboratory', 'lab'], ['RESTORE_LAB', 'Restore a laboratory', 'lab'], ['REDUCE_CAPACITY', 'Reduce laboratory capacity', 'lab+amount'], ['INCREASE_CAPACITY', 'Increase laboratory capacity', 'lab+amount'],
  ['DISABLE_SLOT', 'Disable a time slot', 'slot'], ['RESTORE_SLOT', 'Restore a time slot', 'slot'], ['ADD_EQUIPMENT', 'Add equipment to a laboratory', 'lab+equip'], ['REMOVE_EQUIPMENT', 'Remove equipment (change compatibility)', 'lab+equip'], ['ADD_TEAMS', 'Add new student teams', 'teams'],
];
const csv = (s) => (s || '').split(/[,;]/).map((x) => x.trim()).filter(Boolean);
const delta = (n) => (n > 0 ? `+${n}` : `${n}`);

function describe(m) {
  const t = TYPES.find((x) => x[0] === m.type)[1];
  if (m.type === 'ADD_TEAMS') return `${t}: ${m.count}× ${m.teamCode || 'SIM'} (size ${m.teamSize}, needs ${(m.requirements || []).join('+') || 'nothing'})`;
  return `${t}: ${m.target}${m.amount ? ` by ${m.amount}` : ''}${m.equipment?.length ? ` (${m.equipment.join(', ')})` : ''}`;
}

export default function Simulator() {
  const labs = useApi(() => api.get('/labs')); const slots = useApi(() => api.get('/slots')); const history = useApi(() => api.get('/simulations'));
  const toast = useToast(); const [run, busy] = useAction(toast);
  const [changes, setChanges] = useState([]); const [name, setName] = useState('');
  const [f, setF] = useState({ type: 'DISABLE_LAB', target: '', amount: 1, reason: '', equipment: '', teamCode: 'SURGE', count: 5, teamSize: 3, requirements: '', durationMinutes: 90, eligible: '' });
  const [res, setRes] = useState(null); const [confirm, setConfirm] = useState(false); const [tab, setTab] = useState('build');
  const [picked, setPicked] = useState([]); const [compare, setCompare] = useState(null);
  const kind = TYPES.find((t) => t[0] === f.type)[2];
  const targets = kind.startsWith('slot') ? slots.data || [] : labs.data || [];
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const add = () => {
    const m = { type: f.type, target: kind === 'teams' ? null : f.target || targets[0]?.code, amount: kind.includes('amount') ? Number(f.amount) : null, reason: f.reason || null, equipment: kind.includes('equip') ? csv(f.equipment) : null,
      teamCode: kind === 'teams' ? f.teamCode : null, teamSize: kind === 'teams' ? Number(f.teamSize) : null, requirements: kind === 'teams' ? csv(f.requirements) : null, durationMinutes: kind === 'teams' ? Number(f.durationMinutes) : null, eligibleSlots: kind === 'teams' ? csv(f.eligible) : null, count: kind === 'teams' ? Number(f.count) : null };
    setChanges([...changes, m]); setRes(null);
  };
  const exec = () => run(async () => { setRes(await api.post('/simulations', { name, mutations: changes })); history.reload(); }, 'Scenario recomputed — both baseline and scenario were re-run by Edmonds–Karp');
  const reset = () => { setChanges([]); setRes(null); setName(''); toast.ok('Scenario reset — live data untouched'); };
  const apply = () => run(async () => { await api.post('/simulations/apply', { name, mutations: changes }); setChanges([]); setRes(null); labs.reload(); slots.reload(); }, 'Scenario applied to live data');
  const loadCompare = () => run(async () => setCompare(await Promise.all(picked.map(async (id) => ({ id, ...(await api.get(`/simulations/${id}`)) })))));
  const togglePick = (id) => setPicked(picked.includes(id) ? picked.filter((x) => x !== id) : [...picked, id].slice(-4));

  const r = res?.result; const d = r?.diff;
  return (
    <>
      <PageHeader title="What-if disruption simulator" subtitle="Compose disruptions and recovery actions, then re-run maximum flow on a copy of the live data. Nothing changes until you explicitly apply a scenario." />
      <Tabs tabs={[{ id: 'build', label: 'Scenario builder' }, { id: 'compare', label: `Compare scenarios${history.data?.length ? ` (${history.data.length})` : ''}` }]} value={tab} onChange={setTab} />
      {tab === 'build' ? (
        <div className="grid gap-6 xl:grid-cols-[400px_1fr]">
          <div className="space-y-6">
            <Card><CardHeader title="Add a change" icon={Plus} />
              <div className="space-y-3 p-4">
                <Field label="Change type"><select className="input" value={f.type} onChange={(e) => setF({ ...f, type: e.target.value, target: '' })}>{TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></Field>
                {kind !== 'teams' && <Field label={kind.startsWith('slot') ? 'Time slot' : 'Laboratory'}><select className="input" value={f.target || targets[0]?.code || ''} onChange={set('target')}>{targets.map((t) => <option key={t.code} value={t.code}>{t.code} — {t.name || t.label}</option>)}</select></Field>}
                {kind.includes('amount') && <Field label="Amount (teams per slot)"><input type="number" min="1" className="input" value={f.amount} onChange={set('amount')} /></Field>}
                {kind.includes('equip') && <Field label="Equipment tags" hint="Comma-separated"><input className="input" value={f.equipment} onChange={set('equipment')} placeholder="gpu, linux" /></Field>}
                {(f.type.startsWith('DISABLE')) && <Field label="Disruption reason"><input className="input" value={f.reason} onChange={set('reason')} placeholder="e.g. Power outage" /></Field>}
                {kind === 'teams' && <div className="grid grid-cols-2 gap-3"><Field label="Code prefix"><input className="input" value={f.teamCode} onChange={set('teamCode')} /></Field><Field label="How many"><input type="number" min="1" className="input" value={f.count} onChange={set('count')} /></Field><Field label="Team size"><input type="number" min="1" className="input" value={f.teamSize} onChange={set('teamSize')} /></Field><Field label="Session (min)"><input type="number" min="1" className="input" value={f.durationMinutes} onChange={set('durationMinutes')} /></Field><div className="col-span-2"><Field label="Requires"><input className="input" value={f.requirements} onChange={set('requirements')} placeholder="gpu" /></Field></div><div className="col-span-2"><Field label="Eligible slots" hint="Empty = any"><input className="input" value={f.eligible} onChange={set('eligible')} /></Field></div></div>}
                <button className="btn-ghost w-full" onClick={add} disabled={kind !== 'teams' && targets.length === 0}><Plus size={15} />Add to scenario</button>
              </div></Card>
            <Card><CardHeader title={`Scenario (${changes.length})`} icon={FlaskConical} />
              <div className="space-y-3 p-4">
                {changes.length === 0 ? <p className="text-xs text-ink-400">No changes yet. Add at least one.</p> : <ul className="space-y-1.5">{changes.map((m, i) => <li key={i} className="flex items-start justify-between gap-2 rounded-lg border border-ink-700 bg-ink-900 px-3 py-2 text-xs"><span>{describe(m)}</span><button aria-label="Remove change" className="text-ink-400 hover:text-red-300" onClick={() => setChanges(changes.filter((_, j) => j !== i))}><Trash2 size={13} /></button></li>)}</ul>}
                <Field label="Scenario name"><input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. AI lab outage" /></Field>
                <div className="flex gap-2"><button className="btn-primary flex-1" disabled={!changes.length || busy} onClick={exec}>Run scenario</button><button className="btn-ghost" onClick={reset} title="Discard all changes"><RotateCcw size={15} />Reset</button></div>
              </div></Card>
          </div>
          <div className="min-w-0 space-y-6">
            {!r ? <Card><Empty icon={FlaskConical} title="No scenario result yet" text="Build a scenario and run it. Try disabling the AI lab, then increasing capacity of a general lab to see recovery." /></Card> : (<>
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                <Stat label="Allocated" value={<span>{r.before.allocated} <ArrowRight size={14} className="inline text-ink-400" /> {r.after.allocated}</span>} tone={d.allocatedDelta < 0 ? 'bad' : d.allocatedDelta > 0 ? 'good' : 'default'} sub={`${delta(d.allocatedDelta)} teams`} />
                <Stat label="Unallocated" value={<span>{r.before.unallocatedCount} <ArrowRight size={14} className="inline text-ink-400" /> {r.after.unallocatedCount}</span>} tone={d.unallocatedDelta > 0 ? 'bad' : d.unallocatedDelta < 0 ? 'good' : 'default'} sub={`${delta(d.unallocatedDelta)} teams`} />
                <Stat label="Utilisation" value={<span>{pct(r.before.utilization, 0)} <ArrowRight size={14} className="inline text-ink-400" /> {pct(r.after.utilization, 0)}</span>} tone="brand" sub={`${r.before.totalSeats} → ${r.after.totalSeats} seats`} />
                <Stat label="Displaced / recovered" value={<span className="text-red-300">{d.newlyDisplaced.length}<span className="text-ink-400"> / </span><span className="text-teal-300">{d.newlyRecovered.length}</span></span>} sub={`${d.reassigned.length} moved to another slot`} />
              </div>
              <Card><CardHeader title="Applied changes" hint="Applied to a copy; live data is untouched" /><ul className="space-y-1 p-4 text-xs text-ink-200">{r.changes.map((c, i) => <li key={i}>• {c}</li>)}</ul></Card>
              <div className="grid gap-6 lg:grid-cols-2">
                <Card><CardHeader title="Newly displaced teams" /><div className="flex flex-wrap gap-1.5 p-4">{d.newlyDisplaced.length ? d.newlyDisplaced.map((t) => <Badge key={t} tone="bad">{t}</Badge>) : <span className="text-xs text-ink-400">None</span>}</div></Card>
                <Card><CardHeader title="Newly recovered teams" /><div className="flex flex-wrap gap-1.5 p-4">{d.newlyRecovered.length ? d.newlyRecovered.map((t) => <Badge key={t} tone="good">{t}</Badge>) : <span className="text-xs text-ink-400">None</span>}</div></Card>
              </div>
              <Card><CardHeader title="Bottleneck change" hint="Saturated lab-slot nodes on the min-cut source side" />
                <div className="grid gap-4 p-4 text-xs md:grid-cols-3">{[['Appeared', d.bottleneckAdded, 'bad'], ['Persisting', d.bottleneckPersisting, 'warn'], ['Resolved', d.bottleneckRemoved, 'good']].map(([t, l, tn]) => <div key={t}><span className="label">{t} ({l.length})</span><div className="flex flex-wrap gap-1">{l.slice(0, 24).map((x) => <Badge key={x} tone={tn}>{x}</Badge>)}{l.length > 24 && <span className="text-ink-400">+{l.length - 24} more</span>}</div></div>)}</div>
                <div className="space-y-1 border-t border-ink-700 p-4 text-xs text-ink-200">{r.after.bottleneck.lines.map((l, i) => <p key={i}>{l}</p>)}</div></Card>
              {d.reassigned.length > 0 && <Card><CardHeader title={`Teams moved (${d.reassigned.length})`} /><div className="max-h-48 overflow-auto p-4 text-xs text-ink-200">{d.reassigned.map((m) => <div key={m.team}>{m.team}: {m.fromLab}/{m.fromSlot} <ArrowRight size={11} className="inline" /> {m.toLab}/{m.toSlot}</div>)}</div></Card>}
              <div className="flex justify-end"><button className="btn-danger" disabled={busy} onClick={() => setConfirm(true)}><UploadCloud size={15} />Apply this scenario to live data…</button></div>
            </>)}
          </div>
        </div>
      ) : (
        <Card><CardHeader title="Saved scenarios" hint="Select up to 4 and compare their outcomes side by side" icon={GitCompare} actions={<button className="btn-primary" disabled={picked.length < 2 || busy} onClick={loadCompare}>Compare {picked.length || ''}</button>} />
          {!history.data?.length ? <Empty title="No scenarios saved" text="Every run is stored automatically." /> : <ul className="divide-y divide-ink-700">{history.data.map((h) => <li key={h.id}><label className="flex cursor-pointer items-center gap-3 px-4 py-2.5 hover:bg-ink-800/50"><input type="checkbox" checked={picked.includes(h.id)} onChange={() => togglePick(h.id)} /><span className="min-w-0 flex-1"><span className="block truncate text-sm text-white">{h.name}</span><span className="text-xs text-ink-300">{h.summary}</span></span><span className="text-[11px] text-ink-400">{when(h.createdAt)}</span></label></li>)}</ul>}
          {compare && <div className="space-y-4 border-t border-ink-700 p-4"><div className="h-64"><ResponsiveContainer width="100%" height="100%"><BarChart data={compare.map((c) => ({ name: c.name, Allocated: c.result.after.allocated, Unallocated: c.result.after.unallocatedCount, Baseline: c.result.before.allocated }))}><CartesianGrid stroke="#26334f" strokeDasharray="3 3" vertical={false} /><XAxis dataKey="name" stroke="#94a3bd" fontSize={11} /><YAxis stroke="#94a3bd" fontSize={11} /><Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #26334f', borderRadius: 8, fontSize: 12 }} /><Legend wrapperStyle={{ fontSize: 12 }} /><Bar dataKey="Baseline" fill="#6b7a99" radius={[4, 4, 0, 0]} /><Bar dataKey="Allocated" fill="#2dd4bf" radius={[4, 4, 0, 0]} /><Bar dataKey="Unallocated" fill="#f87171" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div>
            <div className="overflow-x-auto"><table className="w-full min-w-[520px] text-sm"><thead><tr className="border-b border-ink-700"><th className="th">Scenario</th><th className="th">Allocated</th><th className="th">Unallocated</th><th className="th">Utilisation</th><th className="th">Displaced</th><th className="th">Recovered</th></tr></thead><tbody>{compare.map((c) => <tr key={c.id} className="border-b border-ink-700/50"><td className="td">{c.name}</td><td className="td tabular-nums">{c.result.before.allocated} → {c.result.after.allocated}</td><td className="td tabular-nums">{c.result.after.unallocatedCount}</td><td className="td tabular-nums">{pct(c.result.after.utilization)}</td><td className="td tabular-nums">{c.result.diff.newlyDisplaced.length}</td><td className="td tabular-nums">{c.result.diff.newlyRecovered.length}</td></tr>)}</tbody></table></div>
            <Note>Each saved scenario compares against the live data as it was when that scenario ran; if live data changed in between, baselines differ.</Note></div>}
        </Card>)}
      {confirm && <Confirm danger title="Apply scenario to live data?" text="This permanently changes laboratories, time slots and teams in the database (the changes are recorded in the activity log). Use Reset to discard a scenario instead." confirmLabel="Apply to live data" onConfirm={apply} onClose={() => setConfirm(false)} />}
    </>
  );
}
