import { useState, type ChangeEvent, type FormEvent } from 'react';
import { api, formValues } from '../api';
import { failure, type Notice } from '../Message';
import type { UploadTicket } from '../types';

interface Props {
  onNotice: (notice: Notice) => void;
  onSubmitted: () => void;
}

interface Upload {
  key: string;
  fileName: string;
  result?: Notice; // unset while the upload is in flight
}

/** The inspection form. Media goes straight to storage; only the answers go to the API. */
export function InspectionForm({ onNotice, onSubmitted }: Props) {
  const [uploads, setUploads] = useState<Upload[]>([]);
  const [submitting, setSubmitting] = useState(false);
  // One key for this page view, so a double tap or retry submits once.
  const [submitKey] = useState(() => crypto.randomUUID());
  const uploading = uploads.some((u) => !u.result);

  function chooseFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = [...(event.currentTarget.files ?? [])];
    event.currentTarget.value = '';
    files.forEach(upload);
  }

  // Ask for an upload URL, send the file straight to storage, then confirm.
  async function upload(file: File) {
    const key = crypto.randomUUID();
    const finish = (result: Notice) =>
      setUploads((list) => list.map((u) => (u.key === key ? { ...u, result } : u)));
    setUploads((list) => [...list, { key, fileName: file.name }]);
    try {
      const ticket = await api<UploadTicket>('/api/public/media', {
        method: 'POST',
        body: { contentType: file.type, sizeBytes: file.size },
      });
      const put = await fetch(ticket.uploadUrl, {
        method: ticket.method,
        headers: { 'Content-Type': ticket.contentType },
        body: file,
      });
      if (!put.ok) throw new Error(`Upload failed (${put.status})`);
      await api(`/api/public/media/${ticket.mediaId}/confirm`, { method: 'POST' });
      finish({ text: 'uploaded' });
    } catch (error) {
      finish(failure(error));
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (uploading) {
      onNotice({ text: 'Please wait for your uploads to finish.', isError: true });
      return;
    }
    const values = formValues(event.currentTarget);
    setSubmitting(true);
    try {
      await api('/api/public/submit', {
        method: 'POST',
        body: { mileage: Number(values.mileage), conditionNotes: values.conditionNotes },
        headers: { 'Idempotency-Key': submitKey },
      });
      onSubmitted();
    } catch (error) {
      onNotice(failure(error));
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <label>
        Odometer reading (km) <input name="mileage" type="number" min={0} max={2000000} required />
      </label>
      <label style={{ width: '100%' }}>
        Condition notes
        <textarea
          name="conditionNotes"
          maxLength={5000}
          required
          placeholder="Dents, scratches, warning lights, anything we should know"
        />
      </label>
      <label>
        Photos and video (JPEG, PNG, HEIC, MP4)
        <input
          type="file"
          multiple
          accept="image/jpeg,image/png,image/heic,video/mp4"
          onChange={chooseFiles}
        />
      </label>
      <ul>
        {uploads.map((u) => (
          <li key={u.key} className={u.result && (u.result.isError ? 'error' : 'ok')}>
            {u.fileName}: {u.result?.text ?? 'uploading...'}
          </li>
        ))}
      </ul>
      <button disabled={submitting}>Submit inspection</button>
    </form>
  );
}
