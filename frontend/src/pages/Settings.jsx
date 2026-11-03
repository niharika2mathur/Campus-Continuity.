import { useState } from 'react';
import { Database, RefreshCw, ShieldAlert } from 'lucide-react';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Card, CardHeader, Confirm, Loadable, Note, PageHeader, useToast } from '../components/ui.jsx';

const RULES = [
  'A lab–slot node exists only if the laboratory is AVAILABLE, the slot is AVAILABLE and the laboratory capacity is above zero.',
  'A team may use a lab–slot node only if the lab has all required equipment, the team size ≤ lab max team size, session duration ≤ slot duration, and the slot is in the team’s eligible list (or the list is empty).',
  'Lab capacity = number of teams that may use each slot of that lab at the same time.',
  'Objective = maximum number of placed teams. Preferences and travel distance are not optimised.',
  'Request identifiers are case-insensitive and unique; duplicates are rejected after exact verification.',
];

export default function Settings() {
  const health = useApi(() => api.get('/admin/health'));
  const toast = useToast(); const [run, busy] = useAction(toast);
  const [confirm, setConfirm] = useState(null);
  const seed = () => run(async () => { const r = await api.post('/admin/seed?reset=true'); health.reload(); return r; }, 'Sample campus loaded');
  const reset = () => run(async () => { await api.post('/admin/reset'); health.reload(); }, 'All data cleared');

  return (
    <>
      <PageHeader title="Settings & data management" subtitle="Load demo data, reset the database and review the modelling assumptions." />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card><CardHeader title="System status" icon={Database} actions={<button className="btn-ghost" onClick={health.reload}><RefreshCw size={14} /></button>} />
          <Loadable state={health}>{(h) => <dl className="grid grid-cols-2 gap-px bg-ink-700 text-center">{[['Laboratories', h.labs], ['Time slots', h.slots], ['Teams', h.teams], ['Request log entries', h.requestLogEntries], ['Bloom bits', h.bloomBits], ['Bloom insertions', h.bloomInsertions]].map(([k, v]) => <div key={k} className="bg-ink-850 p-3"><dd className="text-lg font-semibold tabular-nums text-white">{v}</dd><dt className="text-[11px] text-ink-300">{k}</dt></div>)}</dl>}</Loadable></Card>
        <Card><CardHeader title="Data management" icon={ShieldAlert} />
          <div className="space-y-3 p-4">
            <button className="btn-primary w-full" disabled={busy} onClick={() => setConfirm('seed')}>Reset &amp; load sample campus</button>
            <button className="btn-danger w-full" disabled={busy} onClick={() => setConfirm('reset')}>Delete all data</button>
            <Note>The sample campus (8 labs, 12 slots, 100 team requests) is imported through the normal validation and Bloom screening path, so it exercises the same code as a real import.</Note>
          </div></Card>
        <Card className="lg:col-span-2"><CardHeader title="Modelling rules & assumptions" />
          <ul className="space-y-2 p-4 text-xs leading-relaxed text-ink-200">{RULES.map((r) => <li key={r}>• {r}</li>)}</ul></Card>
      </div>
      {confirm === 'seed' && <Confirm danger title="Replace all data with the sample campus?" text="Existing laboratories, slots, teams, runs and history will be deleted first." confirmLabel="Reset & load" onConfirm={seed} onClose={() => setConfirm(null)} />}
      {confirm === 'reset' && <Confirm danger title="Delete all data?" text="This removes every laboratory, slot, team, run record and the activity log. It cannot be undone." confirmLabel="Delete everything" onConfirm={reset} onClose={() => setConfirm(null)} />}
    </>
  );
}
