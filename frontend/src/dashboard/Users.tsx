import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, formValues, when } from '../api';
import { failure, Message, type Notice } from '../Message';
import type { User } from '../types';

/** Lists admins and creates new ones. */
export function Users() {
  const [users, setUsers] = useState<User[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);

  const load = useCallback(() => {
    api<User[]>('/api/users').then(setUsers);
  }, []);

  useEffect(load, [load]);

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    try {
      await api('/api/users', { method: 'POST', body: formValues(form) });
      form.reset();
      setNotice({ text: 'User created.' });
      load();
    } catch (error) {
      setNotice(failure(error));
    }
  }

  return (
    <section>
      <h2>Users</h2>
      <form onSubmit={create}>
        <label>
          Email <input name="email" type="email" required />
        </label>
        <label>
          Password <input name="password" type="password" minLength={12} required />
        </label>
        <button>Create user</button>
      </form>
      <Message notice={notice} />
      <table>
        <thead>
          <tr>
            <th>Email</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.email}</td>
              <td>{when(u.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
