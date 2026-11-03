import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import Login from './pages/Login.jsx';
import Overview from './pages/Overview.jsx';
import Resources from './pages/Resources.jsx';
import Teams from './pages/Teams.jsx';
import Requests from './pages/Requests.jsx';
import Allocation from './pages/Allocation.jsx';
import Observatory from './pages/Observatory.jsx';
import Bloom from './pages/Bloom.jsx';
import Simulator from './pages/Simulator.jsx';
import Benchmarks from './pages/Benchmarks.jsx';
import Reports from './pages/Reports.jsx';
import Settings from './pages/Settings.jsx';

// Demo gate only: there is no real authentication (see docs). It records a display name in localStorage.
const Guard = ({ children }) => (localStorage.getItem('cc-user') ? children : <Navigate to="/login" replace />);

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Guard><Layout /></Guard>}>
        <Route index element={<Overview />} />
        <Route path="resources" element={<Resources />} />
        <Route path="teams" element={<Teams />} />
        <Route path="requests" element={<Requests />} />
        <Route path="allocation" element={<Allocation />} />
        <Route path="observatory" element={<Observatory />} />
        <Route path="bloom" element={<Bloom />} />
        <Route path="simulator" element={<Simulator />} />
        <Route path="benchmarks" element={<Benchmarks />} />
        <Route path="reports" element={<Reports />} />
        <Route path="settings" element={<Settings />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
