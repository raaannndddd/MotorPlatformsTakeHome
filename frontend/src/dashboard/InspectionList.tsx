import { when } from '../api';
import type { InspectionSummary } from '../types';

interface Props {
  rows: InspectionSummary[];
  onOpen: (id: string) => void;
}

/** The polled dashboard table. Submitted inspections are highlighted. */
export function InspectionList({ rows, onOpen }: Props) {
  const awaiting = rows.filter((r) => r.status === 'SUBMITTED').length;
  return (
    <>
      <p>{awaiting > 0 && `${awaiting} awaiting your response`}</p>
      <table>
        <thead>
          <tr>
            <th>Client</th>
            <th>Car</th>
            <th>Rego</th>
            <th>Status</th>
            <th>Sent</th>
            <th>Submitted</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const submitted = r.status === 'SUBMITTED';
            const reviewable = submitted || r.status === 'RESPONDED';
            return (
              <tr
                key={r.id}
                className={[submitted && 'submitted', reviewable && 'clickable']
                  .filter(Boolean)
                  .join(' ')}
                onClick={reviewable ? () => onOpen(r.id) : undefined}
              >
                <td>{r.clientName}</td>
                <td>{r.carModel}</td>
                <td>{r.rego}</td>
                <td>{r.status}</td>
                <td>{when(r.createdAt)}</td>
                <td>{when(r.submittedAt)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className="muted">
        Refreshes every 10 seconds. Submitted inspections are highlighted; click one to respond.
      </p>
    </>
  );
}
