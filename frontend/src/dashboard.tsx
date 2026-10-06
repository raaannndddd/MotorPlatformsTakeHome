import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Clients } from './dashboard/Clients';
import { InspectionList } from './dashboard/InspectionList';
import { InspectionReview } from './dashboard/InspectionReview';
import { Users } from './dashboard/Users';
import { Brand, mount } from './mount';
import type { Admin, InspectionSummary } from './types';

const POLL_MS = 10_000;

function Dashboard() {
  const [me, setMe] = useState<Admin | null>(null);
  const [inspections, setInspections] = useState<InspectionSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);

  const loadInspections = useCallback(() => {
    api<InspectionSummary[]>('/api/inspections').then(setInspections);
  }, []);

  useEffect(() => {
    api<Admin>('/api/auth/me').then(setMe, () => (location.href = '/'));
  }, []);

  // Polling, not websockets: the dashboard only needs to notice new submissions.
  useEffect(() => {
    if (!me) return;
    loadInspections();
    const timer = setInterval(loadInspections, POLL_MS);
    return () => clearInterval(timer);
  }, [me, loadInspections]);

  async function logout() {
    await api('/api/auth/logout', { method: 'POST' });
    location.href = '/';
  }

  if (!me) return null;
  return (
    <>
      <header>
        <Brand />
        <div>
          <span className="muted">{me.email}</span>{' '}
          <button className="secondary" onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <h1>Inspections</h1>
      <InspectionList rows={inspections} onOpen={setSelected} />
      {/* Keyed by id so each review starts fresh, with its own idempotency key. */}
      {selected && <InspectionReview key={selected} id={selected} onResponded={loadInspections} />}
      <Clients onLinkSent={loadInspections} />
      <Users />
    </>
  );
}

mount(<Dashboard />);
