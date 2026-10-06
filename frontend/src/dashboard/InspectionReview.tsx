import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { api, formValues } from '../api';
import { failure, Message, type Notice } from '../Message';
import type { InspectionDetail, Media } from '../types';

interface Props {
  id: string;
  onResponded: () => void;
}

/** One submitted inspection: the form answers, the media, and the response form. */
export function InspectionReview({ id, onResponded }: Props) {
  const [detail, setDetail] = useState<InspectionDetail | null>(null);
  const [media, setMedia] = useState<Media[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  // One key per review, so a double click or retry sends the response once.
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const panel = useRef<HTMLElement>(null);

  const load = useCallback(async () => {
    const [d, m] = await Promise.all([
      api<InspectionDetail>(`/api/inspections/${id}`),
      api<Media[]>(`/api/inspections/${id}/media`),
    ]);
    setDetail(d);
    setMedia(m);
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const loaded = detail !== null;
  useEffect(() => {
    if (loaded) panel.current?.scrollIntoView({ behavior: 'smooth' });
  }, [loaded]);

  async function respond(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    try {
      await api(`/api/inspections/${id}/response`, {
        method: 'POST',
        body: formValues(form),
        headers: { 'Idempotency-Key': idempotencyKey },
      });
      form.reset();
      await load();
      setNotice({ text: 'Response sent. The customer will get an SMS.' });
      onResponded();
    } catch (error) {
      setNotice(failure(error));
    }
  }

  if (!detail) return null;
  const i = detail.inspection;
  return (
    <section ref={panel} className="panel">
      <h2>
        {i.clientName}: {i.carModel} ({i.rego})
      </h2>
      <p>
        <strong>Mileage:</strong> {detail.mileage}
      </p>
      <p>
        <strong>Condition notes:</strong> {detail.conditionNotes}
      </p>
      <div className="media">
        {media.length === 0 && <span className="muted">No photos or video.</span>}
        {media.map((m) =>
          m.contentType.startsWith('video/') ? (
            <video key={m.id} src={m.url} controls />
          ) : (
            <a key={m.id} href={m.url} target="_blank" rel="noreferrer">
              <img src={m.url} alt="Inspection photo" />
            </a>
          ),
        )}
      </div>
      {i.status === 'SUBMITTED' && (
        <form onSubmit={respond}>
          <label style={{ width: '100%' }}>
            Response <textarea name="response" maxLength={1000} required />
          </label>
          <button>Send response</button>
        </form>
      )}
      {detail.response && <p>Response sent: {detail.response}</p>}
      <Message notice={notice} />
    </section>
  );
}
