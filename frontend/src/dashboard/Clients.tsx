import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, formValues } from '../api';
import { failure, Message, type Notice } from '../Message';
import type { Client } from '../types';

/** Client CRUD and the "Send inspection link" action. */
export function Clients({ onLinkSent }: { onLinkSent: () => void }) {
  const [clients, setClients] = useState<Client[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);

  const load = useCallback(() => {
    api<Client[]>('/api/clients').then(setClients);
  }, []);

  useEffect(load, [load]);

  async function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    try {
      await api('/api/clients', { method: 'POST', body: formValues(form) });
      form.reset();
      setNotice({ text: 'Client added.' });
      load();
    } catch (error) {
      setNotice(failure(error));
    }
  }

  async function sendLink(client: Client) {
    try {
      await api('/api/inspections', { method: 'POST', body: { clientId: client.id } });
      setNotice({ text: `Inspection link sent to ${client.name}.` });
      onLinkSent();
    } catch (error) {
      setNotice(failure(error));
    }
  }

  async function remove(client: Client) {
    if (!confirm(`Delete ${client.name}?`)) return;
    try {
      await api(`/api/clients/${client.id}`, { method: 'DELETE' });
      load();
    } catch (error) {
      setNotice(failure(error));
    }
  }

  return (
    <section>
      <h2>Clients</h2>
      <form onSubmit={add}>
        <label>
          Name <input name="name" required maxLength={200} />
        </label>
        <label>
          Car model <input name="carModel" required maxLength={100} />
        </label>
        <label>
          Phone <input name="phone" required maxLength={30} placeholder="0412 345 678" />
        </label>
        <label>
          Rego <input name="rego" required maxLength={20} />
        </label>
        <button>Add client</button>
      </form>
      <Message notice={notice} />
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Car</th>
            <th>Rego</th>
            <th>Phone</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {clients.map((c) => (
            <tr key={c.id}>
              <td>{c.name}</td>
              <td>{c.carModel}</td>
              <td>{c.rego}</td>
              <td>{c.phone}</td>
              <td>
                <button onClick={() => sendLink(c)}>Send inspection link</button>{' '}
                <button className="secondary" onClick={() => remove(c)}>
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
