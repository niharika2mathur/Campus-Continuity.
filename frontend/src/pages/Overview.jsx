import { Link } from 'react-router-dom';
import { AlertTriangle, Binary, Boxes, CheckCircle2, Gauge, Users, XCircle, History } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, Legend } from 'recharts';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Badge, Card, CardHeader, Empty, Loadable, Note, PageHeader, Stat, StatusBadge, useToast } from '../components/ui.jsx';
import { pct, when } from '../lib/util.js';

export default function Overview() {
  const s = useApi(() => api.get('/dashboard'));
  const toast = useToast(); const [run, busy] = useAction(toast);
  const seed = () => run(async () => { await api.post('/admin/seed?reset=true'); s.reload(); }, 'Sample data loaded through the real import path');

  return (
    <>
      <PageHeader title="Operations dashboard" subtitle="Every figure below comes from a live Edmonds–Karp run on the current database state — nothing is cached." />
      <Loadable state={s}>{(d) => d.labsTotal === 0 && d.teams === 0 ? (
        <Card><Empty title="No campus data yet" text="Load the bundled sample campus (8 laboratories, 12 time slots, 100 team requests) or import your own CSV files in the Allocation studio."
          action={<button className="btn-primary" onClick={seed} disabled={busy}>Load sample data</button>} /></Card>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-6">
            <Stat label="Laboratories" value={d.labsTotal} icon={Boxes} sub={`${d.labsAvailable} available`} />
            <Stat label="Disrupted" value={d.labsDisrupted + d.slotsDisrupted} tone={d.labsDisrupted + d.slotsDisrupted ? 'warn' : 'good'} icon={AlertTriangle} sub={`${d.labsDisrupted} labs · ${d.slotsDisrupted} slots`} />
            <Stat label="Incoming requests" value={d.teams} icon={Users} />
            <Stat label="Allocated" value={d.allocated} tone="good" icon={CheckCircle2} sub="maximum feasible" tip="Maximum-cardinality allocation from Edmonds–Karp" />
            <Stat label="Unallocated" value={d.unallocated} tone={d.unallocated ? 'bad' : 'good'} icon={XCircle} />
            <Stat label="Utilisation" value={pct(d.utilization)} tone="brand" icon={Gauge} sub={`${d.allocated} of ${d.totalSeats} seats`} />
          </div>
          <div className="grid gap-6 xl:grid-cols-3">
            <Card className="xl:col-span-2">
              <CardHeader title="Laboratory load" hint={`Seats used vs available per laboratory · plan computed in ${d.planMillis} ms`} icon={Gauge} />
              <div className="h-72 p-4">
                {d.labLoads.length === 0 ? <Empty title="No available seats" text="Every laboratory is disrupted, deactivated or has zero capacity." /> : (
                  <ResponsiveContainer width="100%" height="100%"><BarChart data={d.labLoads} margin={{ left: -10 }}>
                    <CartesianGrid stroke="#26334f" strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="lab" stroke="#94a3bd" fontSize={11} tickLine={false} /><YAxis stroke="#94a3bd" fontSize={11} tickLine={false} axisLine={false} />
                    <Tooltip contentStyle={{ background: '#0f172a', border: '1px solid #26334f', borderRadius: 8, fontSize: 12 }} cursor={{ fill: 'rgba(59,130,246,0.08)' }} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Bar dataKey="used" name="Seats used" fill="#2dd4bf" radius={[4, 4, 0, 0]} /><Bar dataKey="seats" name="Seats available" fill="#3b82f6" fillOpacity={0.45} radius={[4, 4, 0, 0]} />
                  </BarChart></ResponsiveContainer>)}
              </div>
            </Card>
            <Card>
              <CardHeader title="Current disruptions" icon={AlertTriangle} />
              {d.disruptions.length === 0 ? <Empty title="No active disruptions" text="All laboratories and time slots are available." /> : (
                <ul className="divide-y divide-ink-700">{d.disruptions.map((x) => (
                  <li key={x.kind + x.code} className="flex items-start justify-between gap-3 px-4 py-3">
                    <div><div className="text-sm font-medium text-white">{x.code} <span className="text-xs font-normal text-ink-400">{x.kind.toLowerCase()}</span></div><div className="text-xs text-ink-300">{x.reason || 'No reason recorded'}</div></div><StatusBadge status={x.status} /></li>))}</ul>)}
            </Card>
          </div>
          <div className="grid gap-6 xl:grid-cols-3">
            <Card><CardHeader title="Why teams are unallocated" hint="Derived from the minimum cut" icon={XCircle} />
              <div className="space-y-2 p-4">{d.bottleneck.map((l, i) => <p key={i} className="text-xs leading-relaxed text-ink-200">{l}</p>)}<Link className="inline-block pt-1 text-xs text-brand-400 hover:underline" to="/observatory">Open in the Algorithm observatory →</Link></div></Card>
            <Card><CardHeader title="Recent simulation & run activity" icon={History} />
              {d.recentRuns.length === 0 ? <Empty title="No runs yet" text="Run an allocation or a what-if scenario." action={<Link className="btn-ghost" to="/allocation">Open Allocation studio</Link>} /> : (
                <ul className="divide-y divide-ink-700">{d.recentRuns.slice(0, 6).map((r) => <li key={r.id} className="px-4 py-2.5"><div className="flex items-center justify-between gap-2"><span className="truncate text-sm text-white">{r.name}</span><Badge tone="brand">{r.kind}</Badge></div><div className="text-xs text-ink-300">{r.summary}</div><div className="text-[11px] text-ink-400">{when(r.createdAt)}</div></li>)}</ul>)}</Card>
            <Card><CardHeader title="Request screening (live Bloom filter)" icon={Binary} />
              <div className="grid grid-cols-2 gap-px bg-ink-700 text-center">{[['Screened', d.bloom.queries], ['Definite negatives', d.bloom.negativePrechecks], ['Possible positives', d.bloom.possiblePositives], ['Confirmed duplicates', d.bloom.confirmedDuplicates], ['False positives', d.bloom.falsePositives], ['False-positive rate', pct(d.bloom.falsePositiveRate, 2)]].map(([k, v]) => <div key={k} className="bg-ink-850 p-3"><div className="text-lg font-semibold tabular-nums text-white">{v}</div><div className="text-[11px] text-ink-300">{k}</div></div>)}</div>
              <div className="p-3"><Note>Counters restart when the server restarts; the filter itself is rebuilt from the database.</Note></div></Card>
          </div>
        </div>)}</Loadable>
    </>
  );
}
