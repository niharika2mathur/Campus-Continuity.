import { useState } from 'react';
import { AlertTriangle, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Badge, Card, Confirm, Empty, Field, Loadable, Modal, Note, PageHeader, Table, THead, useToast } from '../components/ui.jsx';
import { when } from '../lib/util.js';

const blank = { requestId: '', code: '', name: '', size: 3, requirements: '', durationMinutes: 90, eligibleSlots: '' };
const tags = (s) => (Array.isArray(s) ? s : s.split(/[,;]/).map((x) => x.trim()).filter(Boolean));

export default function Teams() {
  const teams = useApi(() => api.get('/teams'));
  const toast = useToast(); const [run, busy] = useAction(toast);
  const [edit, setEdit] = useState(null); const [errs, setErrs] = useState({}); const [q, setQ] = useState('');
  const [del, setDel] = useState(null); const [result, setResult] = useState(null); const [strict, setStrict] = useState(false);
  const f = (k) => (e) => setEdit({ ...edit, form: { ...edit.form, [k]: e.target.value } });

  const save = async () => {
    setErrs({}); setResult(null);
    const b = { ...edit.form, size: Number(edit.form.size), durationMinutes: Number(edit.form.durationMinutes), requirements: tags(edit.form.requirements), eligibleSlots: tags(edit.form.eligibleSlots) };
    try {
      if (edit.id) { await api.put(`/teams/${edit.id}`, b); toast.ok('Team updated'); setEdit(null); }
      else { const r = await api.post(`/teams${strict ? '?strict=true' : ''}`, b); setResult(r); toast.ok('Request accepted'); }
      teams.reload();
    } catch (e) { setErrs(e.fieldErrors || {}); toast.err(e.message); teams.reload(); }
  };
  const list = (teams.data || []).filter((t) => !q || `${t.code} ${t.name} ${t.requestId}`.toLowerCase().includes(q.toLowerCase()));

  return (
    <>
      <PageHeader title="Student teams" subtitle="Each team carries one resource request. New requests are screened by the Bloom filter and verified against the database before they are accepted."
        actions={<button className="btn-primary" onClick={() => { setResult(null); setEdit({ form: { ...blank, requestId: `REQ-${Date.now().toString().slice(-6)}` } }); }}><Plus size={16} />Register team</button>} />
      <Card>
        <div className="border-b border-ink-700 p-3"><div className="relative max-w-sm"><Search size={15} className="absolute left-3 top-2.5 text-ink-400" /><input className="input !pl-9" placeholder="Search code, name or request id" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search teams" /></div></div>
        <Loadable state={teams}>{() => list.length === 0 ? <Empty title={teams.data.length ? 'No matching teams' : 'No teams registered'} text={teams.data.length ? 'Try a different search.' : 'Register a team, or import teams.csv in the Allocation studio.'} /> : (
          <Table><THead cols={['Team', 'Request id', 'Size', 'Requires', 'Session', 'Eligible slots', 'Created', '']} /><tbody>{list.map((t) => (
            <tr key={t.id} className="border-b border-ink-700/60 hover:bg-ink-800/50"><td className="td"><span className="font-medium">{t.code}</span><div className="text-xs text-ink-400">{t.name}</div></td><td className="td font-mono text-xs text-ink-300">{t.requestId}</td><td className="td tabular-nums">{t.size}</td>
              <td className="td"><div className="flex flex-wrap gap-1">{t.requirements.length ? t.requirements.map((r) => <Badge key={r} tone="brand">{r}</Badge>) : <span className="text-xs text-ink-400">any lab</span>}</div></td>
              <td className="td tabular-nums">{t.durationMinutes} min</td><td className="td text-xs text-ink-300">{t.eligibleSlots.length ? t.eligibleSlots.join(', ') : 'any slot'}</td><td className="td text-xs text-ink-400">{when(t.createdAt)}</td>
              <td className="td"><div className="flex justify-end gap-1.5"><button className="btn-ghost !px-2" aria-label={`Edit ${t.code}`} onClick={() => { setResult(null); setEdit({ id: t.id, form: { ...t, requirements: t.requirements.join(', '), eligibleSlots: t.eligibleSlots.join(', ') } }); }}><Pencil size={14} /></button><button className="btn-danger !px-2" aria-label={`Delete ${t.code}`} onClick={() => setDel(t)}><Trash2 size={14} /></button></div></td></tr>))}</tbody></Table>)}</Loadable>
      </Card>
      {edit && (
        <Modal title={edit.id ? 'Edit team' : 'Register team request'} onClose={() => setEdit(null)} footer={<><button className="btn-ghost" onClick={() => setEdit(null)}>{result ? 'Close' : 'Cancel'}</button>{!result && <button className="btn-primary" disabled={busy} onClick={save}>{edit.id ? 'Save' : 'Submit request'}</button>}</>}>
          {result ? (
            <div className="space-y-3"><Note tone="good"><b>{result.team.code}</b> accepted. Bloom filter said <b>{result.screening.bloomResult}</b>{result.screening.bloomResult === 'POSSIBLE' ? ' (possible positive → exact database check found it absent: a false positive, request still accepted)' : ' (definitely new, no database lookup needed)'}.</Note>
              {result.warnings.map((w) => <Note key={w} tone="warn">{w}</Note>)}</div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Request id" error={errs.requestId} hint={edit.id ? 'Identifiers cannot be changed.' : 'Screened for duplicates.'}><input className="input font-mono" disabled={!!edit.id} value={edit.form.requestId} onChange={f('requestId')} /></Field>
              <Field label="Team code" error={errs.code}><input className="input" value={edit.form.code} onChange={f('code')} /></Field>
              <Field label="Team name"><input className="input" value={edit.form.name} onChange={f('name')} /></Field>
              <Field label="Team size" error={errs.size}><input type="number" min="1" className="input" value={edit.form.size} onChange={f('size')} /></Field>
              <Field label="Session duration (min)" error={errs.durationMinutes}><input type="number" min="1" className="input" value={edit.form.durationMinutes} onChange={f('durationMinutes')} /></Field>
              <Field label="Requires (equipment/software)" hint="Comma-separated"><input className="input" value={edit.form.requirements} onChange={f('requirements')} /></Field>
              <div className="sm:col-span-2"><Field label="Eligible slot codes" hint="Comma-separated; empty = any slot"><input className="input" value={edit.form.eligibleSlots} onChange={f('eligibleSlots')} /></Field></div>
              {!edit.id && <label className="flex items-center gap-2 text-xs text-ink-200 sm:col-span-2"><input type="checkbox" checked={strict} onChange={(e) => setStrict(e.target.checked)} />Reject requests that no laboratory could ever host (strict validation)</label>}
              {Object.keys(errs).length > 0 && <div className="sm:col-span-2"><Note tone="warn"><AlertTriangle size={0} />Please fix the highlighted fields.</Note></div>}
            </div>)}
        </Modal>)}
      {del && <Confirm danger title={`Delete ${del.code}?`} text="The team is removed from future allocations. Its request id stays in the Bloom filter (filters cannot delete); a later submission of the same id will be caught as a false positive by the exact check and accepted." confirmLabel="Delete" onConfirm={() => run(async () => { await api.del(`/teams/${del.id}`); teams.reload(); }, 'Team deleted')} onClose={() => setDel(null)} />}
    </>
  );
}
