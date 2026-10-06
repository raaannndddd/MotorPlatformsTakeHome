import { useState, type FormEvent } from 'react';
import { api, formValues } from '../api';
import { failure, type Notice } from '../Message';

interface Props {
  token: string;
  onNotice: (notice: Notice) => void;
  onVerified: () => void;
}

/** Texts a one-time code to the phone on file and exchanges it for a customer session cookie. */
export function OtpStep({ token, onNotice, onVerified }: Props) {
  const [codeSent, setCodeSent] = useState(false);

  async function sendCode() {
    try {
      await api('/api/public/otp', { method: 'POST', body: { token } });
      setCodeSent(true);
      onNotice({ text: 'Code sent. It expires in 5 minutes.' });
    } catch (error) {
      onNotice(failure(error));
    }
  }

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      const body = { token, ...formValues(event.currentTarget) };
      await api('/api/public/otp/verify', { method: 'POST', body });
      onVerified();
    } catch (error) {
      onNotice(failure(error));
    }
  }

  return (
    <section>
      <p>To protect your details, we'll text a 6-digit code to the phone number we have on file.</p>
      <button onClick={sendCode}>{codeSent ? 'Send a new code' : 'Text me a code'}</button>
      {codeSent && (
        <form onSubmit={verify}>
          <label>
            Code{' '}
            <input
              name="code"
              inputMode="numeric"
              pattern="\d{6}"
              maxLength={6}
              autoComplete="one-time-code"
              required
            />
          </label>
          <button>Continue</button>
        </form>
      )}
    </section>
  );
}
