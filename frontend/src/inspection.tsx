import { useEffect, useState } from 'react';
import { api } from './api';
import { InspectionForm } from './inspection/InspectionForm';
import { OtpStep } from './inspection/OtpStep';
import { failure, Message, type Notice } from './Message';
import { Brand, mount } from './mount';

/** The link token from /i/{token}. */
const token = location.pathname.split('/').pop() ?? '';

type Step = 'loading' | 'otp' | 'form' | 'done';

function InspectionPage() {
  const [step, setStep] = useState<Step>('loading');
  const [notice, setNotice] = useState<Notice | null>(null);

  useEffect(() => {
    async function start() {
      // This call (not the page load itself) is what marks the inspection as opened.
      try {
        await api('/api/public/session', { method: 'POST', body: { token } });
      } catch (error) {
        setNotice(failure(error));
        return;
      }
      try {
        await api('/api/public/inspection'); // Already verified in this browser?
        setStep('form');
      } catch {
        setStep('otp');
      }
    }
    start();
  }, []);

  function goTo(next: Step) {
    setNotice(null);
    setStep(next);
  }

  return (
    <>
      <header>
        <Brand />
      </header>
      <h1>Car inspection</h1>
      <Message notice={notice} />
      {step === 'otp' && (
        <OtpStep token={token} onNotice={setNotice} onVerified={() => goTo('form')} />
      )}
      {step === 'form' && <InspectionForm onNotice={setNotice} onSubmitted={() => goTo('done')} />}
      {step === 'done' && (
        <p className="ok">
          Thanks! Your inspection has been sent. We'll text you when we've reviewed it.
        </p>
      )}
    </>
  );
}

mount(<InspectionPage />);
