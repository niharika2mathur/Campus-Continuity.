import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity, ShieldCheck } from 'lucide-react';
import { Note } from '../components/ui.jsx';

export default function Login() {
  const nav = useNavigate();
  const [name, setName] = useState('Demo Administrator');
  const enter = (e) => { e.preventDefault(); localStorage.setItem('cc-user', JSON.stringify({ name: name.trim() || 'Demo Administrator', role: 'ADMIN' })); nav('/'); };
  return (
    <div className="grid min-h-full place-items-center bg-ink-950 p-4">
      <div className="fade-in w-full max-w-md">
        <div className="mb-6 flex items-center gap-3">
          <div className="grid h-11 w-11 place-items-center rounded-xl border border-ink-600 bg-ink-800"><Activity className="text-teal-400" /></div>
          <div><h1 className="text-lg font-semibold text-white">Campus Continuity</h1><p className="text-xs text-ink-300">Intelligent campus resource recovery &amp; allocation</p></div>
        </div>
        <form onSubmit={enter} className="card space-y-4 p-6">
          <div><h2 className="text-base font-semibold text-white">Enter the control room</h2><p className="mt-1 text-sm text-ink-300">Maximum-flow allocation, Bloom-filter request screening and what-if disruption analysis.</p></div>
          <label className="block"><span className="label">Display name</span><input className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} /></label>
          <button className="btn-primary w-full" type="submit"><ShieldCheck size={16} />Continue as administrator</button>
          <Note>Demo entry only — this academic build has no user accounts or passwords, and the name is kept in your browser. Do not expose it to untrusted networks.</Note>
        </form>
      </div>
    </div>
  );
}
