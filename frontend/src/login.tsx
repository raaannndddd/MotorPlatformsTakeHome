import { useState, type FormEvent } from 'react';
import { api, formValues } from './api';
import { failure, Message, type Notice } from './Message';
import { Brand, mount } from './mount';

function LoginPage() {
  const [notice, setNotice] = useState<Notice | null>(null);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      await api('/api/auth/login', { method: 'POST', body: formValues(event.currentTarget) });
      location.href = '/dashboard.html';
    } catch (error) {
      setNotice(failure(error));
    }
  }

  return (
    <>
      <header>
        <Brand />
      </header>
      <h1>Admin portal</h1>
      <form onSubmit={login}>
        <label>
          Email <input name="email" type="email" autoComplete="username" required />
        </label>
        <label>
          Password{' '}
          <input name="password" type="password" autoComplete="current-password" required />
        </label>
        <button>Log in</button>
      </form>
      <Message notice={notice} />
    </>
  );
}

mount(<LoginPage />);
